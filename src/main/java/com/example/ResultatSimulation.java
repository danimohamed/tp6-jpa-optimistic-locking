package com.example;

import com.example.model.Reservation;

/**
 * Résultat structuré d'une simulation de concurrence, renvoyé par les classes de
 * démonstration. Il permet aux tests de vérifier le déroulement sans dépendre du
 * format exact des messages affichés.
 */
public class ResultatSimulation {

    private final boolean conflitDetecte;
    private final String messageConflit;
    private final Reservation etatFinal;
    private final int tentatives;

    public ResultatSimulation(boolean conflitDetecte,
                              String messageConflit,
                              Reservation etatFinal,
                              int tentatives) {
        this.conflitDetecte = conflitDetecte;
        this.messageConflit = messageConflit;
        this.etatFinal = etatFinal;
        this.tentatives = tentatives;
    }

    /**
     * @return {@code true} si la simulation s'est terminée sur un conflit non
     *         rattrapé. Dans le scénario avec retry réussi, la valeur est
     *         {@code false} alors que {@link #getTentatives()} vaut 1 : le
     *         conflit a bien eu lieu, mais il a été absorbé par le retry.
     */
    public boolean isConflitDetecte() {
        return conflitDetecte;
    }

    public String getMessageConflit() {
        return messageConflit;
    }

    /**
     * @return l'état de la réservation relu en base à la fin de la simulation
     */
    public Reservation getEtatFinal() {
        return etatFinal;
    }

    /**
     * @return le nombre de retries consommés (0 si aucune simulation avec retry)
     */
    public int getTentatives() {
        return tentatives;
    }
}
