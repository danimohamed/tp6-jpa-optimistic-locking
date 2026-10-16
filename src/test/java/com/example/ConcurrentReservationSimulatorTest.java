package com.example;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie le scénario de lost update joué par
 * {@link ConcurrentReservationSimulator} : la seconde transaction doit être
 * rejetée et l'état final rester celui de la gagnante.
 */
public class ConcurrentReservationSimulatorTest extends AbstractJpaTest {

    @Test
    public void leConflitEstDetecte() {
        ResultatSimulation resultat = new ConcurrentReservationSimulator(service).executer();

        assertTrue("Le conflit d'optimistic locking doit être détecté", resultat.isConflitDetecte());
        assertNotNull(resultat.getMessageConflit());
    }

    @Test
    public void leMessageDeConflitEstCeluiDHibernate() {
        ResultatSimulation resultat = new ConcurrentReservationSimulator(service).executer();

        assertTrue("Message inattendu : " + resultat.getMessageConflit(),
                resultat.getMessageConflit()
                        .contains("Row was updated or deleted by another transaction"));
    }

    @Test
    public void letatFinalEstCeluiDeLaTransactionGagnante() {
        ResultatSimulation resultat = new ConcurrentReservationSimulator(service).executer();

        assertEquals("Salle B", resultat.getEtatFinal().getSalle());
        assertEquals(1, resultat.getEtatFinal().getVersion());
    }

    @Test
    public void uneSeuleReservationEstConcernee() {
        new ConcurrentReservationSimulator(service).executer();

        assertEquals(1, compterReservations());
    }

    @Test
    public void aucuneTentativeDeRetryDansCeScenario() {
        ResultatSimulation resultat = new ConcurrentReservationSimulator(service).executer();

        assertEquals(0, resultat.getTentatives());
    }

    @Test
    public void laSortieStandardRaconteLeScenario() {
        try (Capture capture = new Capture()) {
            new ConcurrentReservationSimulator(service)
                    .executer(capture.fluxSortie(), capture.fluxErreur());

            String sortie = capture.sortie();
            assertTrue(sortie.contains("Réservation créée :"));
            assertTrue(sortie.contains("Transaction 1 a lu : version=0"));
            assertTrue(sortie.contains("Transaction 2 a lu : version=0"));
            assertTrue(sortie.contains("Transaction 1 : modification réussie."));
            assertTrue(sortie.contains("Nouvelle version en base : 1"));
            assertTrue(sortie.contains("État final :"));
            assertTrue(sortie.contains("version=1"));
            assertFalse("Le conflit ne doit pas être écrit sur stdout",
                    sortie.contains("conflit détecté"));
        }
    }

    @Test
    public void leConflitEstEcritSurLaSortieDErreur() {
        try (Capture capture = new Capture()) {
            new ConcurrentReservationSimulator(service)
                    .executer(capture.fluxSortie(), capture.fluxErreur());

            String erreur = capture.erreur();
            assertTrue(erreur.contains("Transaction 2 : conflit détecté - "));
            assertTrue(erreur.contains("Row was updated or deleted by another transaction"));
        }
    }
}
