package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;

import org.junit.Test;

import javax.persistence.OptimisticLockException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie la stratégie de reprise : recharger la version courante, réappliquer
 * l'intention, puis réessayer — jusqu'à
 * {@link ReservationRetryHandler#MAX_RETRIES} tentatives.
 */
public class ReservationRetryHandlerTest extends AbstractJpaTest {

    @Test
    public void leRetryAboutitApresUnConflit() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());

        gagnante.setSalle("Salle B");
        service.modifierReservation(gagnante);

        perdante.setSalle("Salle C");
        Reservation resultat = new ReservationRetryHandler(service).modifierAvecRetry(perdante);

        assertEquals("Salle C", resultat.getSalle());
        assertEquals(2, resultat.getVersion());
        assertEquals("Salle C", service.trouverParId(gagnante.getId()).getSalle());
    }

    @Test
    public void leRetryReappliqueVraimentLIntentionInitiale() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());

        gagnante.setSalle("Salle B");
        service.modifierReservation(gagnante);

        // L'intention porte sur la salle ET sur le créneau.
        perdante.setSalle("Salle C");
        perdante.setDateDebut(DEBUT.plusHours(4));
        perdante.setDateFin(DEBUT.plusHours(6));

        Reservation resultat = new ReservationRetryHandler(service).modifierAvecRetry(perdante);

        assertEquals("Salle C", resultat.getSalle());
        assertEquals(DEBUT.plusHours(4), resultat.getDateDebut());
        assertEquals(DEBUT.plusHours(6), resultat.getDateFin());
    }

    @Test
    public void leNombreDeRetriesEstExpose() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());

        gagnante.setSalle("Salle B");
        service.modifierReservation(gagnante);
        perdante.setSalle("Salle C");

        ReservationRetryHandler handler = new ReservationRetryHandler(service);
        handler.modifierAvecRetry(perdante);

        assertEquals(1, handler.getDernierNombreDeRetries());
    }

    @Test
    public void chaqueTentativeEstJournalisee() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());
        gagnante.setSalle("Salle B");
        service.modifierReservation(gagnante);
        perdante.setSalle("Salle C");

        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        PrintStream journal = new PrintStream(tampon, true, StandardCharsets.UTF_8);

        new ReservationRetryHandler(service).modifierAvecRetry(perdante, journal);

        String contenu = new String(tampon.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(contenu.contains("Conflit détecté, tentative de retry 1"));
    }

    @Test
    public void unEchecPersistantLeveUneOptimisticLockExceptionApresTroisTentatives() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation copie = service.trouverParId(gagnante.getId());

        // Service qui refuse toute écriture : le handler doit abandonner
        // exactement après MAX_RETRIES tentatives.
        ReservationService serviceQuiEchoue = new ServiceQuiEchoueToujours(service, copie);

        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        PrintStream journal = new PrintStream(tampon, true, StandardCharsets.UTF_8);

        ReservationRetryHandler handler = new ReservationRetryHandler(serviceQuiEchoue);
        OptimisticLockException e = assertThrows(OptimisticLockException.class,
                () -> handler.modifierAvecRetry(copie, journal));

        assertEquals("Échec après " + ReservationRetryHandler.MAX_RETRIES + " tentatives", e.getMessage());
        assertEquals(ReservationRetryHandler.MAX_RETRIES, handler.getDernierNombreDeRetries());

        String contenu = new String(tampon.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(contenu.contains("tentative de retry 1"));
        assertTrue(contenu.contains("tentative de retry 2"));
        assertTrue(contenu.contains("tentative de retry 3"));
    }

    @Test
    public void uneReservationDisparuePendantLeRetryArreteLaBoucle() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());

        // Le conflit est permanent et la relecture ne trouve plus rien.
        ReservationService serviceQuiSupprime = new ServiceQuiEchoueToujours(service, null);
        ReservationRetryHandler handler = new ReservationRetryHandler(serviceQuiSupprime);

        OptimisticLockException e = assertThrows(OptimisticLockException.class,
                () -> handler.modifierAvecRetry(perdante));

        assertNotNull(e.getMessage());
        assertTrue("Message inattendu : " + e.getMessage(), e.getMessage().contains("supprimée"));
        assertEquals("Un seul retry a eu lieu avant l'abandon",
                1, handler.getDernierNombreDeRetries());
    }

    @Test
    public void uneReservationSansIdentifiantEstRefusee() {
        Reservation sansId = new Reservation("Salle X", DEBUT, DEBUT.plusHours(1));

        assertThrows(IllegalArgumentException.class,
                () -> new ReservationRetryHandler(service).modifierAvecRetry(sansId));
    }

    @Test
    public void uneReservationNulleEstRefusee() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReservationRetryHandler(service).modifierAvecRetry(null));
    }

    @Test
    public void leRetryConserveLeNombreDeLignes() {
        Reservation gagnante = creerReservation("Salle A");
        Reservation perdante = service.trouverParId(gagnante.getId());
        gagnante.setSalle("Salle B");
        service.modifierReservation(gagnante);
        perdante.setSalle("Salle C");

        new ReservationRetryHandler(service).modifierAvecRetry(perdante);

        assertEquals(1, compterReservations());
    }

    /**
     * Service qui refuse toute modification et ne sait relire que la copie qu'on
     * lui a fournie : il simule un conflit permanent sans dépendre du
     * comportement réel d'Hibernate.
     */
    private static final class ServiceQuiEchoueToujours implements ReservationService {

        private final ReservationService delegate;
        private final Reservation aRelire;

        private ServiceQuiEchoueToujours(ReservationService delegate, Reservation aRelire) {
            this.delegate = delegate;
            this.aRelire = aRelire;
        }

        @Override
        public Reservation creerReservation(Reservation reservation) {
            return delegate.creerReservation(reservation);
        }

        @Override
        public Reservation modifierReservation(Reservation reservation) {
            throw new OptimisticLockException(
                    "Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect)");
        }

        @Override
        public Reservation trouverParId(Long id) {
            return aRelire;
        }

        @Override
        public List<Reservation> listerReservations() {
            return delegate.listerReservations();
        }
    }
}
