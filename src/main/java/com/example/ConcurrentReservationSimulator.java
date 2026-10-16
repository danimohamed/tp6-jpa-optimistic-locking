package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;
import com.example.util.Formats;

import javax.persistence.OptimisticLockException;
import java.io.PrintStream;
import java.time.LocalDateTime;

/**
 * Rejoue le scénario d'un <em>lost update</em> : deux utilisateurs lisent la même
 * réservation, le premier valide sa modification, le second travaille encore sur
 * la copie qu'il avait lue et sa modification est refusée.
 *
 * <p>La simulation est volontairement <strong>séquentielle</strong> : les deux
 * copies détachées sont lues avant que la première ne soit modifiée, ce qui
 * reproduit exactement l'état de deux transactions concurrentes sans dépendre de
 * l'ordonnancement d'un thread. Le scénario est donc déterministe.</p>
 */
public class ConcurrentReservationSimulator {

    private final ReservationService service;

    public ConcurrentReservationSimulator() {
        this(new ReservationServiceImpl());
    }

    public ConcurrentReservationSimulator(ReservationService service) {
        this.service = service;
    }

    public ResultatSimulation executer() {
        return executer(System.out, System.err);
    }

    /**
     * Exécute la simulation en redirigeant les messages vers les flux fournis.
     *
     * @param sortie flux des messages normaux
     * @param erreur flux des messages d'erreur (le conflit est une anomalie)
     */
    public ResultatSimulation executer(PrintStream sortie, PrintStream erreur) {
        // 1) Données de départ : une salle réservée demain, de +2h à +4h.
        LocalDateTime debut = LocalDateTime.now().plusDays(1).plusHours(2).withSecond(0).withNano(0);
        LocalDateTime fin = debut.plusHours(2);

        Reservation reservation = new Reservation("Salle A", debut, fin);
        service.creerReservation(reservation);
        sortie.println("Réservation créée : " + Formats.reservation(reservation));
        sortie.println();

        // 2) Deux transactions lisent la même ligne : même id, même version.
        Reservation tx1 = service.trouverParId(reservation.getId());
        Reservation tx2 = service.trouverParId(reservation.getId());
        sortie.println("Transaction 1 a lu : version=" + tx1.getVersion());
        sortie.println("Transaction 2 a lu : version=" + tx2.getVersion());
        sortie.println();

        // 3) Transaction 1 modifie et valide : la version passe à 1.
        tx1.setSalle("Salle B");
        Reservation apresTx1 = service.modifierReservation(tx1);
        sortie.println("Transaction 1 : modification réussie.");
        sortie.println("Nouvelle version en base : " + apresTx1.getVersion());
        sortie.println();

        // 4) Transaction 2 travaille sur sa copie périmée (version 0).
        boolean conflitDetecte = false;
        String messageConflit = null;
        tx2.setSalle("Salle C");
        try {
            service.modifierReservation(tx2);
            sortie.println("Transaction 2 : modification réussie.");
        } catch (OptimisticLockException e) {
            conflitDetecte = true;
            messageConflit = e.getMessage();
            erreur.println("Transaction 2 : conflit détecté - " + messageConflit);
        }

        // 5) L'état final doit rester celui de la transaction gagnante.
        Reservation etatFinal = service.trouverParId(reservation.getId());
        sortie.println();
        sortie.println("État final : " + Formats.reservation(etatFinal));

        return new ResultatSimulation(conflitDetecte, messageConflit, etatFinal, 0);
    }
}
