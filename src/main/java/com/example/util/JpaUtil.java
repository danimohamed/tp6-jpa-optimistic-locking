package com.example.util;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * Point d'accès unique à l'{@link EntityManagerFactory} de l'unité de persistance
 * {@code reservationPU} (déclarée dans {@code META-INF/persistence.xml}).
 *
 * <p>Créer une EMF est coûteux : on la crée une seule fois et on la réutilise.
 * Elle est recréée à la demande si elle a été fermée, ce qui permet aux tests de
 * libérer les ressources sans casser les exécutions suivantes.</p>
 */
public final class JpaUtil {

    public static final String UNITE_DE_PERSISTANCE = "reservationPU";

    private static EntityManagerFactory emf;

    private JpaUtil() {
    }

    public static synchronized EntityManagerFactory entityManagerFactory() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory(UNITE_DE_PERSISTANCE);
        }
        return emf;
    }

    public static EntityManager ouvrirEntityManager() {
        return entityManagerFactory().createEntityManager();
    }

    public static synchronized void fermer() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
        emf = null;
    }
}
