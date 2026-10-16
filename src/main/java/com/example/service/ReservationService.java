package com.example.service;

import com.example.model.Reservation;

import java.util.List;

/**
 * Contrat de gestion des réservations.
 *
 * <p>Chaque méthode ouvre et referme son propre {@code EntityManager} : les
 * entités retournées sont donc <em>détachées</em> dès le retour de la méthode.
 * C'est exactement la situation qui produit un conflit d'optimistic locking
 * lorsqu'un client modifie une copie devenue obsolète.</p>
 */
public interface ReservationService {

    /**
     * Persiste une nouvelle réservation et retourne l'instance gérée (identifiant
     * et version renseignés).
     */
    Reservation creerReservation(Reservation reservation);

    /**
     * Applique les modifications d'une réservation existante.
     *
     * <p>La copie détachée est fusionnée via {@code merge} : seuls les champs
     * réellement modifiés sont écrits. Fusionner une copie identique à l'état
     * chargé n'émet donc aucun {@code UPDATE} et la version reste inchangée —
     * ce n'est pas un cas de conflit.</p>
     *
     * @return la réservation détachée, version à jour telle que persistée
     * @throws IllegalArgumentException si la réservation n'existe pas
     * @throws javax.persistence.OptimisticLockException si la version détenue par
     *         l'appelant ne correspond plus à celle de la base
     */
    Reservation modifierReservation(Reservation reservation);

    /**
     * @return la réservation détachée, ou {@code null} si l'identifiant est inconnu
     */
    Reservation trouverParId(Long id);

    /**
     * @return toutes les réservations, triées par identifiant
     */
    List<Reservation> listerReservations();
}
