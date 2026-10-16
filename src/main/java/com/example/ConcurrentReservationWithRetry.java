package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;
import com.example.util.Formats;

import javax.persistence.OptimisticLockException;
import java.io.PrintStream;
import java.time.LocalDateTime;

/**
 * Même scénario que {@link ConcurrentReservationSimulator}, mais la transaction
 * perdante ne renonce pas : elle recharge la version courante et réapplique son
 * intention jusqu'à {@link ReservationRetryHandler#MAX_RETRIES} fois.
 *
 * <p>C'est la contre-mesure recommandée contre un conflit d'optimistic locking
 * sur une opération dont l'intention métier est idempotente : « réserver cette
 * salle à ce créneau » reste vrai après un rechargement, contrairement à un
 * incrément relatif qui devrait être recalculé.</p>
 */
public class ConcurrentReservationWithRetry {

    private final ReservationService service;
    private final ReservationRetryHandler retryHandler;

    public ConcurrentReservationWithRetry() {
        this(new ReservationServiceImpl());
    }

    public ConcurrentReservationWithRetry(ReservationService service) {
        this.service = service;
        this.retryHandler = new ReservationRetryHandler(service);
    }

    public ResultatSimulation executer() {
        return executer(System.out, System.err);
    }

    public ResultatSimulation executer(PrintStream sortie, PrintStream erreur) {
        LocalDateTime debut = LocalDateTime.now().plusDays(2).plusHours(2).withSecond(0).withNano(0);
        LocalDateTime fin = debut.plusHours(2);

        Reservation reservation = new Reservation("Salle A", debut, fin);
        service.creerReservation(reservation);
        sortie.println("Réservation créée : " + Formats.reservation(reservation));
        sortie.println();

        Reservation tx1 = service.trouverParId(reservation.getId());
        Reservation tx2 = service.trouverParId(reservation.getId());

        // Transaction 1 gagne la course.
        tx1.setSalle("Salle B");
        service.modifierReservation(tx1);
        sortie.println("Transaction 1 : modification réussie.");
        sortie.println();

        // Transaction 2 perd la course mais tente de se rattraper.
        tx2.setSalle("Salle C");
        boolean conflitDetecte = false;
        String messageConflit = null;
        int tentatives = 0;
        try {
            retryHandler.modifierAvecRetry(tx2, sortie);
            sortie.println("Transaction 2 : modification réussie après retry.");
        } catch (OptimisticLockException e) {
            conflitDetecte = true;
            messageConflit = e.getMessage();
            erreur.println("Transaction 2 : échec après retries - " + messageConflit);
        }
        tentatives = retryHandler.getDernierNombreDeRetries();

        Reservation etatFinal = service.trouverParId(reservation.getId());
        sortie.println();
        sortie.println("État final : " + Formats.reservation(etatFinal));

        return new ResultatSimulation(conflitDetecte, messageConflit, etatFinal, tentatives);
    }
}
