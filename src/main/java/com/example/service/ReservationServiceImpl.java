package com.example.service;

import com.example.model.Reservation;
import com.example.util.JpaUtil;

import org.hibernate.StaleStateException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import java.util.List;

/**
 * Implémentation JPA de {@link ReservationService}.
 *
 * <p>Chaque méthode suit le même schéma : ouvrir un {@code EntityManager},
 * démarrer une transaction, agir, commiter, puis fermer dans un {@code finally}.
 * Les transactions sont volontairement très courtes — une transaction longue
 * augmenterait la fenêtre de conflit.</p>
 */
public class ReservationServiceImpl implements ReservationService {

    private final EntityManagerFactory emf;

    /**
     * Constructeur par défaut : utilise l'unité de persistance {@code reservationPU}
     * (soit {@code Persistence.createEntityManagerFactory("reservationPU")}).
     */
    public ReservationServiceImpl() {
        this(JpaUtil.entityManagerFactory());
    }

    /**
     * Constructeur d'injection, utilisé par les tests et par les démonstrations
     * qui veulent partager une fabrique déjà ouverte.
     */
    public ReservationServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public Reservation creerReservation(Reservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("La réservation ne peut pas être nulle");
        }
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(reservation);
            tx.commit();
            return reservation;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public Reservation modifierReservation(Reservation reservation) {
        if (reservation == null || reservation.getId() == null) {
            throw new IllegalArgumentException("Réservation non trouvée");
        }
        Long id = reservation.getId();
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            // 1) La réservation doit exister : sinon merge() la considérerait
            //    comme transitoire et tenterait une insertion.
            Reservation existante = em.find(Reservation.class, id);
            if (existante == null) {
                throw new IllegalArgumentException("Réservation non trouvée");
            }

            // 2) On fusionne la copie détachée. C'est ici que l'optimistic
            //    locking entre en jeu : si la version portée par la copie
            //    détachée ne correspond plus à celle lue en base, Hibernate
            //    refuse la fusion (StaleObjectStateException, convertie en
            //    OptimisticLockException côté JPA).
            Reservation fusionnee = em.merge(reservation);

            tx.commit();

            // 3) La version n'est incrémentée qu'au flush : c'est le commit
            //    qui la reporte sur l'instance gérée, désormais détachée.
            return fusionnee;
        } catch (OptimisticLockException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } catch (StaleStateException e) {
            // Filet de sécurité : selon le moment où le conflit est détecté
            // (fusion ou flush), Hibernate peut laisser échapper l'exception
            // Hibernate native.
            if (tx.isActive()) {
                tx.rollback();
            }
            throw new OptimisticLockException(e.getMessage(), e);
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public Reservation trouverParId(Long id) {
        if (id == null) {
            return null;
        }
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(Reservation.class, id);
        } finally {
            em.close();
        }
    }

    @Override
    public List<Reservation> listerReservations() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT r FROM Reservation r ORDER BY r.id", Reservation.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }
}
