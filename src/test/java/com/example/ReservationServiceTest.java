package com.example;

import com.example.model.Reservation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie le contrat CRUD de {@link com.example.service.ReservationService}
 * indépendamment de toute concurrence.
 */
public class ReservationServiceTest extends AbstractJpaTest {

    @Test
    public void creerPuisRelireUneReservation() {
        Reservation creee = creerReservation("Salle A");

        Reservation relue = service.trouverParId(creee.getId());

        assertNotNull(relue);
        assertEquals("Salle A", relue.getSalle());
        assertEquals(DEBUT, relue.getDateDebut());
        assertEquals(DEBUT.plusHours(2), relue.getDateFin());
        assertEquals(0, relue.getVersion());
    }

    @Test
    public void creerUneReservationNulleEstRefuse() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.creerReservation(null));

        assertNotNull(e.getMessage());
    }

    @Test
    public void creerUneReservationSansSalleEstRefuseeParLeSchema() {
        // La colonne salle est NOT NULL : la violation remonte au commit.
        assertThrows(RuntimeException.class,
                () -> service.creerReservation(new Reservation(null, DEBUT, DEBUT.plusHours(1))));
        assertEquals(0, compterReservations());
    }

    @Test
    public void trouverParIdInconnuRetourneNull() {
        assertNull(service.trouverParId(9999L));
    }

    @Test
    public void trouverParIdNulRetourneNull() {
        assertNull(service.trouverParId(null));
    }

    @Test
    public void modifierUneReservationAbsenteEstRefuse() {
        Reservation fantome = new Reservation("Salle X", DEBUT, DEBUT.plusHours(1));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.modifierReservation(fantome));

        assertEquals("Réservation non trouvée", e.getMessage());
    }

    @Test
    public void modifierUneReservationSansIdentifiantEstRefuse() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.modifierReservation(new Reservation("Salle X", DEBUT, DEBUT.plusHours(1))));

        assertEquals("Réservation non trouvée", e.getMessage());
    }

    @Test
    public void modifierUneReservationNulleEstRefuse() {
        assertThrows(IllegalArgumentException.class, () -> service.modifierReservation(null));
    }

    @Test
    public void listerRenvoieToutesLesReservationsTrieesParIdentifiant() {
        creerReservation("Salle A");
        creerReservation("Salle B");
        creerReservation("Salle C");

        List<Reservation> toutes = service.listerReservations();

        assertEquals(3, toutes.size());
        assertEquals("Salle A", toutes.get(0).getSalle());
        assertEquals("Salle B", toutes.get(1).getSalle());
        assertEquals("Salle C", toutes.get(2).getSalle());
        assertTrue(toutes.get(0).getId() < toutes.get(1).getId());
        assertTrue(toutes.get(1).getId() < toutes.get(2).getId());
    }

    @Test
    public void listerSurUneTableVideRenvoieUneListeVide() {
        assertEquals(0, service.listerReservations().size());
    }

    @Test
    public void modifierChangeLaSalleEtLeCreneau() {
        Reservation creee = creerReservation("Salle A");
        creee.setSalle("Salle Z");
        creee.setDateDebut(DEBUT.plusHours(3));
        creee.setDateFin(DEBUT.plusHours(4));

        Reservation modifiee = service.modifierReservation(creee);

        assertEquals("Salle Z", modifiee.getSalle());
        assertEquals(DEBUT.plusHours(3), modifiee.getDateDebut());
        assertEquals(DEBUT.plusHours(4), modifiee.getDateFin());
        assertEquals(1, modifiee.getVersion());

        Reservation relue = service.trouverParId(creee.getId());
        assertEquals("Salle Z", relue.getSalle());
    }

    @Test
    public void uneReservationNonTrouveeNestJamaisInseree() {
        Reservation fantome = new Reservation("Salle X", DEBUT, DEBUT.plusHours(1));
        assertThrows(IllegalArgumentException.class, () -> service.modifierReservation(fantome));

        assertEquals("merge() sur un identifiant inconnu ne doit pas créer de ligne",
                0, compterReservations());
    }
}
