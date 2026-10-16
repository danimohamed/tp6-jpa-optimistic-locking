package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;
import com.example.util.Formats;
import com.example.util.JpaUtil;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Programme principal du TP 6 : il enchaîne les deux démonstrations.
 *
 * <ol>
 *   <li>sans gestion de conflit — la seconde transaction perd sa modification ;</li>
 *   <li>avec {@link ReservationRetryHandler} — la seconde transaction recharge et
 *       réapplique son intention, donc elle aboutit.</li>
 * </ol>
 *
 * <p>Le conflit est signalé sur {@code System.err}, comme une anomalie : les deux
 * flux sont donc volontairement séparés pour que la sortie standard reste le
 * récit nominal de la démonstration.</p>
 */
public final class OptimisticLockingApp {

    private OptimisticLockingApp() {
    }

    public static void main(String[] args) {
        PrintStream sortie = System.out;
        PrintStream erreur = System.err;

        // Hibernate 5.6 journalise via JUL : sans cela, les messages INFO de
        // démarrage polluent stderr et noient le message de conflit.
        Logger.getLogger("org.hibernate").setLevel(Level.WARNING);
        Logger.getLogger("org.hibernate.orm.deprecation").setLevel(Level.OFF);

        try {
            titre(sortie, "1. Conflit sans gestion : la modification de la transaction 2 est perdue");
            viderReservations();
            new ConcurrentReservationSimulator().executer(sortie, erreur);

            sortie.println();
            titre(sortie, "2. Conflit géré par retry : la transaction 2 recharge et réapplique son intention");
            viderReservations();
            new ConcurrentReservationWithRetry().executer(sortie, erreur);

            sortie.println();
            titre(sortie, "État final de la base");
            ReservationService service = new ReservationServiceImpl();
            List<Reservation> toutes = service.listerReservations();
            for (Reservation r : toutes) {
                sortie.println("  " + Formats.reservation(r));
            }
            sortie.println("  total = " + toutes.size() + " réservation(s)");
        } finally {
            JpaUtil.fermer();
        }
    }

    private static void titre(PrintStream sortie, String texte) {
        sortie.println("=== " + texte + " ===");
    }

    /**
     * Repart d'une table vide pour que les deux démonstrations soient
     * comparables : la seconde ne doit pas hériter des lignes de la première.
     */
    private static void viderReservations() {
        EntityManager em = JpaUtil.ouvrirEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM Reservation r").executeUpdate();
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
        // H2 n'oublie pas le compteur d'identité après un DELETE : on le remet à
        // zéro pour que chaque démonstration reparte de id=1.
        EntityManager em2 = JpaUtil.ouvrirEntityManager();
        EntityTransaction tx2 = em2.getTransaction();
        try {
            tx2.begin();
            em2.createNativeQuery("ALTER TABLE reservations ALTER COLUMN id RESTART WITH 1")
                    .executeUpdate();
            tx2.commit();
        } catch (RuntimeException e) {
            if (tx2.isActive()) {
                tx2.rollback();
            }
        } finally {
            em2.close();
        }
    }
}
