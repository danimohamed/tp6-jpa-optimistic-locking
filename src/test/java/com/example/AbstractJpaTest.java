package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;
import com.example.util.JpaUtil;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.time.LocalDateTime;

/**
 * Socle commun aux tests : une {@link EntityManagerFactory} partagée par classe,
 * une table {@code reservations} vidée avant et après chaque test, et quelques
 * fabriques de données.
 *
 * <p>La base H2 est en mémoire avec {@code DB_CLOSE_DELAY=-1} : elle survit à la
 * fermeture de l'EMF, donc le nettoyage explicite des lignes est indispensable
 * d'une classe de tests à l'autre.</p>
 */
public abstract class AbstractJpaTest {

    /** Date de départ utilisée par les tests : demain à 10h00, sans nanos. */
    protected static final LocalDateTime DEBUT =
            LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);

    protected static EntityManagerFactory emf;
    protected ReservationService service;

    @BeforeClass
    public static void ouvrirEntityManagerFactory() {
        emf = JpaUtil.entityManagerFactory();
    }

    @AfterClass
    public static void fermerEntityManagerFactory() {
        JpaUtil.fermer();
    }

    @Before
    public void preparer() {
        viderReservations();
        service = new ReservationServiceImpl(emf);
    }

    @After
    public void nettoyer() {
        viderReservations();
    }

    /** Crée et persiste une réservation de deux heures. */
    protected Reservation creerReservation(String salle) {
        return service.creerReservation(new Reservation(salle, DEBUT, DEBUT.plusHours(2)));
    }

    protected long compterReservations() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT COUNT(r) FROM Reservation r", Long.class).getSingleResult();
        } finally {
            em.close();
        }
    }

    /**
     * Supprime toutes les lignes puis remet le compteur d'identité à zéro, pour
     * que chaque test reparte d'un {@code id=1} prévisible.
     */
    protected void viderReservations() {
        executerJpql("DELETE FROM Reservation r");
        executerSql("ALTER TABLE reservations ALTER COLUMN id RESTART WITH 1");
    }

    private void executerJpql(String requete) {
        executer(requete, false);
    }

    private void executerSql(String requete) {
        executer(requete, true);
    }

    private void executer(String requete, boolean natif) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (natif) {
                em.createNativeQuery(requete).executeUpdate();
            } else {
                em.createQuery(requete).executeUpdate();
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
