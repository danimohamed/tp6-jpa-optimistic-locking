package com.example.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.time.LocalDateTime;

/**
 * Entité représentant la réservation d'une salle sur un intervalle de temps.
 *
 * <p>Le champ {@code version} est annoté {@link Version} : Hibernate l'incrémente
 * automatiquement à chaque UPDATE et ajoute la clause {@code and version=?} à la
 * requête. C'est le support de l'<em>optimistic locking</em> : si la valeur lue
 * ne correspond plus à celle stockée, l'UPDATE ne touche aucune ligne et Hibernate
 * lève une {@link javax.persistence.OptimisticLockException}.</p>
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String salle;

    @Column(nullable = false)
    private LocalDateTime dateDebut;

    @Column(nullable = false)
    private LocalDateTime dateFin;

    @Version
    private int version;

    public Reservation() {
    }

    public Reservation(String salle, LocalDateTime dateDebut, LocalDateTime dateFin) {
        this.salle = salle;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    public Long getId() {
        return id;
    }

    public String getSalle() {
        return salle;
    }

    public void setSalle(String salle) {
        this.salle = salle;
    }

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDateTime dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDateTime dateFin) {
        this.dateFin = dateFin;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Reservation{id=" + id
                + ", salle='" + salle + '\''
                + ", dateDebut=" + dateDebut
                + ", dateFin=" + dateFin
                + ", version=" + version
                + '}';
    }
}
