package com.example.util;

import com.example.model.Reservation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Petit utilitaire d'affichage des dates et des réservations, partagé par les
 * démonstrations.
 */
public final class Formats {

    private static final DateTimeFormatter AFFICHAGE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Formats() {
    }

    public static String date(LocalDateTime valeur) {
        return valeur == null ? "null" : AFFICHAGE.format(valeur);
    }

    /**
     * @return une ligne lisible décrivant une réservation, version incluse
     */
    public static String reservation(Reservation r) {
        if (r == null) {
            return "aucune réservation";
        }
        return "id=" + r.getId()
                + ", salle=" + r.getSalle()
                + ", dateDebut=" + date(r.getDateDebut())
                + ", dateFin=" + date(r.getDateFin())
                + ", version=" + r.getVersion();
    }
}
