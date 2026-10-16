package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;

import javax.persistence.OptimisticLockException;
import java.io.PrintStream;
import java.time.LocalDateTime;

/**
 * Rejoue une modification perdue à cause d'un conflit d'optimistic locking.
 *
 * <p>Le principe : quand {@code merge} rejette une copie détachée parce que sa
 * version est périmée, la copie est définitivement inutilisable. Il faut donc
 * <strong>recharger</strong> l'entité pour récupérer la version courante, puis
 * <strong>réappliquer</strong> l'intention métier initiale avant de réessayer.</p>
 *
 * <p>C'est précisément ce que ne fait pas le pseudo-code du support :
 * {@code reservation.setSalle(reservation.getSalle())} réaffecte la valeur
 * fraîchement rechargée à elle-même et n'applique donc aucun changement. Ici,
 * l'intention est mémorisée avant le rechargement puis réappliquée après.</p>
 */
public class ReservationRetryHandler {

    public static final int MAX_RETRIES = 3;

    private final ReservationService service;

    /** Nombre de retries consommés par le dernier appel, pour l'observabilité. */
    private int dernierNombreDeRetries;

    public ReservationRetryHandler(ReservationService service) {
        this.service = service;
    }

    /**
     * @return le nombre de retries consommés lors du dernier appel à
     *         {@link #modifierAvecRetry(Reservation)}
     */
    public int getDernierNombreDeRetries() {
        return dernierNombreDeRetries;
    }

    /**
     * Tente de modifier la réservation jusqu'à {@value #MAX_RETRIES} fois.
     *
     * @param reservation copie détachée portant l'intention de modification
     * @return la réservation modifiée (version à jour)
     * @throws OptimisticLockException si toutes les tentatives ont échoué
     */
    public Reservation modifierAvecRetry(Reservation reservation) {
        return modifierAvecRetry(reservation, System.out);
    }

    public Reservation modifierAvecRetry(Reservation reservation, PrintStream journal) {
        if (reservation == null || reservation.getId() == null) {
            throw new IllegalArgumentException("Réservation non trouvée");
        }

        // L'intention du client, mémorisée avant tout rechargement.
        final String salleVoulue = reservation.getSalle();
        final LocalDateTime debutVoulu = reservation.getDateDebut();
        final LocalDateTime finVoulue = reservation.getDateFin();

        Reservation courante = reservation;
        int tentative = 0;
        dernierNombreDeRetries = 0;
        while (tentative < MAX_RETRIES) {
            try {
                return service.modifierReservation(courante);
            } catch (OptimisticLockException e) {
                tentative++;
                dernierNombreDeRetries = tentative;
                journal.println("Conflit détecté, tentative de retry " + tentative);

                Reservation rechargee = service.trouverParId(reservation.getId());
                if (rechargee == null) {
                    throw new OptimisticLockException(
                            "Réservation " + reservation.getId() + " supprimée entre-temps", e);
                }
                // On réapplique l'intention sur la version fraîche.
                rechargee.setSalle(salleVoulue);
                rechargee.setDateDebut(debutVoulu);
                rechargee.setDateFin(finVoulue);
                courante = rechargee;
            }
        }
        throw new OptimisticLockException("Échec après " + MAX_RETRIES + " tentatives");
    }
}
