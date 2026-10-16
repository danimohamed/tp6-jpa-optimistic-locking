package com.example;

import com.example.model.Reservation;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Vérifie le scénario complet « conflit puis reprise » : la transaction
 * perdante recharge la version courante et finit par s'appliquer.
 */
public class ConcurrentReservationWithRetryTest extends AbstractJpaTest {

    @Test
    public void leRetryRattrapeLaTransactionPerdante() {
        ResultatSimulation resultat = new ConcurrentReservationWithRetry(service).executer();

        assertFalse("Le conflit a été absorbé par le retry", resultat.isConflitDetecte());
        assertNull(resultat.getMessageConflit());
        assertEquals("Salle C", resultat.getEtatFinal().getSalle());
        assertEquals(2, resultat.getEtatFinal().getVersion());
    }

    @Test
    public void leNombreDeTentativesRefleteLeConflitAbsorbe() {
        ResultatSimulation resultat = new ConcurrentReservationWithRetry(service).executer();

        assertEquals(1, resultat.getTentatives());
    }

    @Test
    public void laSortieNarreLeConflitPuisLaReprise() {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ByteArrayOutputStream erreur = new ByteArrayOutputStream();

        try (PrintStream fluxSortie = new PrintStream(sortie, true, StandardCharsets.UTF_8);
             PrintStream fluxErreur = new PrintStream(erreur, true, StandardCharsets.UTF_8)) {

            new ConcurrentReservationWithRetry(service).executer(fluxSortie, fluxErreur);
        }

        String contenu = new String(sortie.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(contenu.contains("Réservation créée :"));
        assertTrue(contenu.contains("Transaction 1 : modification réussie."));
        assertTrue(contenu.contains("Conflit détecté, tentative de retry 1"));
        assertTrue(contenu.contains("Transaction 2 : modification réussie après retry."));
        assertTrue(contenu.contains("État final :"));
        assertTrue(contenu.contains("salle=Salle C"));
        assertTrue(contenu.contains("version=2"));
        assertFalse("Aucun échec ne doit être annoncé : " + contenu, contenu.contains("échec"));

        assertEquals("", new String(erreur.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    public void lEtatFinalEstCeluiDeLaTransactionRattrapee() {
        ResultatSimulation resultat = new ConcurrentReservationWithRetry(service).executer();

        assertNotNull(resultat.getEtatFinal().getId());
        Reservation enBase = service.trouverParId(resultat.getEtatFinal().getId());
        assertEquals("Salle C", enBase.getSalle());
        assertEquals(2, enBase.getVersion());
        assertEquals(resultat.getEtatFinal().getDateDebut(), enBase.getDateDebut());
    }

    @Test
    public void uneSeuleReservationResteEnBase() {
        new ConcurrentReservationWithRetry(service).executer();

        assertEquals(1, compterReservations());
    }

    @Test
    public void laVersionFinaleTraduitLesDeuxEcrituresReussies() {
        ResultatSimulation resultat = new ConcurrentReservationWithRetry(service).executer();

        // version 0 à la création, 1 pour la transaction gagnante,
        // 2 pour la transaction rattrapée par le retry.
        assertEquals(2, resultat.getEtatFinal().getVersion());
        assertEquals(1, resultat.getTentatives());
    }

    @Test
    public void leCreneauInitialEstConserveApresLeRetry() {
        ResultatSimulation resultat = new ConcurrentReservationWithRetry(service).executer();

        Reservation etatFinal = resultat.getEtatFinal();
        assertNotNull(etatFinal.getDateDebut());
        assertEquals(etatFinal.getDateDebut().plusHours(2), etatFinal.getDateFin());
    }
}
