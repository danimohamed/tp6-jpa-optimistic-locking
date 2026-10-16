package com.example;

import com.example.model.Reservation;

import org.junit.Test;

import javax.persistence.OptimisticLockException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie le comportement de {@code @Version} : valeur initiale, incrément à
 * chaque UPDATE, et refus d'une copie détachée périmée.
 */
public class ReservationVersionTest extends AbstractJpaTest {

    @Test
    public void laVersionInitialeEstAZero() {
        Reservation creee = creerReservation("Salle A");

        assertNotNull("L'identité doit être générée", creee.getId());
        assertEquals(0, creee.getVersion());
    }

    @Test
    public void laVersionEstIncrementeeAPresChaqueModification() {
        Reservation creee = creerReservation("Salle A");

        creee.setSalle("Salle B");
        Reservation apres = service.modifierReservation(creee);
        assertEquals(1, apres.getVersion());

        Reservation relue = service.trouverParId(creee.getId());
        assertEquals("La version doit être persistée", 1, relue.getVersion());
    }

    @Test
    public void uneFusionSansChangementNIncrementePasLaVersion() {
        Reservation creee = creerReservation("Salle A");

        // Hibernate compare l'état fusionné à l'instantané de chargement : sans
        // champ modifié, aucun UPDATE n'est émis et la version ne bouge pas.
        Reservation inchangee = service.modifierReservation(creee);

        assertEquals(0, inchangee.getVersion());
        assertEquals(0, service.trouverParId(creee.getId()).getVersion());
    }

    @Test
    public void laVersionProgresseALaSuiteDeTroisModifications() {
        Reservation courante = creerReservation("Salle A");

        for (int i = 1; i <= 3; i++) {
            courante.setSalle("Salle " + (char) ('A' + i));
            courante = service.modifierReservation(courante);
            assertEquals(i, courante.getVersion());
        }

        assertEquals(3, service.trouverParId(courante.getId()).getVersion());
    }

    @Test
    public void uneCopieDetacheePerimeeEstRejetee() {
        Reservation gagnante = creerReservation("Salle A");

        // Deux lectures : chacune repart de la version 0.
        Reservation tx1 = service.trouverParId(gagnante.getId());
        Reservation tx2 = service.trouverParId(gagnante.getId());
        assertEquals(0, tx1.getVersion());
        assertEquals(0, tx2.getVersion());

        tx1.setSalle("Salle B");
        service.modifierReservation(tx1);

        tx2.setSalle("Salle C");
        assertThrows(OptimisticLockException.class, () -> service.modifierReservation(tx2));
    }

    @Test
    public void leMessageDeConflitEstCeluiDHibernate() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());
        gagnante.setSalle("Salle B");
        service.modifierReservation(gagnante);

        OptimisticLockException e = assertThrows(OptimisticLockException.class,
                () -> service.modifierReservation(perdante));

        assertNotNull(e.getMessage());
        // Hibernate 5.6 suffixe le message par « : [com.example.model.Reservation#1] »,
        // donc on vérifie une sous-chaîne et non l'égalité.
        assertTrue("Message inattendu : " + e.getMessage(),
                e.getMessage().contains("Row was updated or deleted by another transaction"));
    }

    @Test
    public void laModificationGagnanteEstConserveeApresLeConflit() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());

        gagnante.setSalle("Salle B");
        service.modifierReservation(gagnante);

        perdante.setSalle("Salle C");
        assertThrows(OptimisticLockException.class, () -> service.modifierReservation(perdante));

        Reservation enBase = service.trouverParId(gagnante.getId());
        assertEquals("La modification perdante ne doit pas écraser la gagnante",
                "Salle B", enBase.getSalle());
        assertEquals(1, enBase.getVersion());
    }

    @Test
    public void leCreneauGagnantResteIntactApresLeConflit() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());

        // La perdante tente de changer le créneau, pas seulement la salle.
        perdante.setDateDebut(DEBUT.plusHours(5));
        perdante.setDateFin(DEBUT.plusHours(7));

        gagnante.setSalle("Salle B");
        service.modifierReservation(gagnante);

        assertThrows(OptimisticLockException.class, () -> service.modifierReservation(perdante));

        Reservation enBase = service.trouverParId(gagnante.getId());
        assertEquals("Le créneau gagnant doit rester intact", DEBUT, enBase.getDateDebut());
        assertEquals(DEBUT.plusHours(2), enBase.getDateFin());
    }
}
