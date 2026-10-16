package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationServiceImpl;
import com.example.util.JpaUtil;

import org.junit.AfterClass;
import org.junit.Test;

import javax.persistence.EntityManager;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie la démonstration complète, de bout en bout : les deux scénarios
 * enchaînés par {@link OptimisticLockingApp#main(String[])} et l'état final de la
 * base.
 *
 * <p>Cette classe n'étend volontairement pas {@link AbstractJpaTest} :
 * {@code main} ferme l'{@code EntityManagerFactory} à la fin, il ne faut donc pas
 * partager celui du socle.</p>
 */
public class OptimisticLockingAppTest {

    @AfterClass
    public static void fermerEntityManagerFactory() {
        JpaUtil.fermer();
    }

    @Test
    public void laDemonstrationEnchaineLesDeuxRecits() {
        String sortie = executer();

        assertTrue(sortie.contains("=== 1. Conflit sans gestion : la modification de la transaction 2 est perdue ==="));
        assertTrue(sortie.contains("=== 2. Conflit géré par retry : la transaction 2 recharge et réapplique son intention ==="));
        assertTrue(sortie.contains("=== État final de la base ==="));
        assertTrue(sortie.contains("total = 1 réservation(s)"));
    }

    @Test
    public void laPremiereDemonstrationPerdLaModification() {
        String sortie = executer();
        String premierRecit = sortie.substring(0, sortie.indexOf("=== 2."));

        assertTrue(premierRecit.contains("Transaction 1 : modification réussie."));
        assertTrue(premierRecit.contains("salle=Salle B"));
        assertFalse("La transaction 2 perdante ne doit pas aboutir : " + premierRecit,
                premierRecit.contains("modification réussie après retry"));
    }

    @Test
    public void laSecondeDemonstrationRattrapeLaModification() {
        String sortie = executer();

        assertTrue(sortie.contains("Conflit détecté, tentative de retry 1"));
        assertTrue(sortie.contains("Transaction 2 : modification réussie après retry."));
        assertTrue(sortie.contains("salle=Salle C"));
        assertTrue(sortie.contains("version=2"));
    }

    @Test
    public void leConflitEstSignaleSurLaSortieDErreur() {
        try (Capture capture = new Capture()) {
            OptimisticLockingApp.main(new String[0]);

            String sortie = capture.sortie();
            String erreur = capture.erreur();

            assertTrue(erreur.contains("Transaction 2 : conflit détecté - "));
            assertTrue(erreur.contains("Row was updated or deleted by another transaction"));
            assertFalse("Le récit nominal ne doit pas contenir le conflit : " + sortie,
                    sortie.contains("conflit détecté -"));
        }
    }

    @Test
    public void uneSeuleReservationSubsisteALaFin() {
        executer();

        assertEquals(1L, compterReservations());
    }

    private String executer() {
        try (Capture capture = new Capture()) {
            OptimisticLockingApp.main(new String[0]);
            return capture.sortie();
        }
    }

    private long compterReservations() {
        EntityManager em = JpaUtil.entityManagerFactory().createEntityManager();
        try {
            return em.createQuery("SELECT COUNT(r) FROM Reservation r", Long.class).getSingleResult();
        } finally {
            em.close();
        }
    }

    /** Garde-fou : les entités relues doivent porter la version courante. */
    @Test
    public void lEtatFinalEstReluAvecSaVersion() {
        executer();

        List<Reservation> toutes = new ReservationServiceImpl().listerReservations();
        assertEquals(1, toutes.size());
        assertEquals(2, toutes.get(0).getVersion());
    }
}
