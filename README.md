# TP 6 — Simuler un conflit de réservation concurrent et démontrer l'optimistic locking avec `@Version`

Implémentation complète du **TP 6** du cours *JPA / Hibernate* (MLIAEdu — cours 28, lab 149).

Le projet démontre, sur une entité `Reservation` versionnée, le **problème de la mise à jour perdue** (*lost update*) et la manière dont le **verrouillage optimiste** de JPA/Hibernate le détecte :

1. **Conflit non géré** — deux transactions lisent la même ligne (`version=0`), la première écrit, la seconde écrase silencieusement la première : la modification de la transaction 2 est perdue.
2. **Conflit géré** — la transaction 2 détecte le conflit (`OptimisticLockException`), **recharge** la ligne (désormais `version=1`) et **réapplique son intention** : sa modification est conservée, l'état final est cohérent (`version=2`).

---

## Prérequis

| Outil | Version minimale | Vérifié avec |
|---|---|---|
| JDK | 11 | 21.0.7 (Temurin) |
| Maven | 3.8 | 3.9.9 |
| Hibernate ORM | 5.6 | 5.6.15.Final (téléchargé par Maven) |
| H2 Database | 2.1 | 2.1.214 (téléchargé par Maven) |

Aucun serveur de base de données n'est nécessaire : H2 est embarqué en mémoire (`jdbc:h2:mem:testdb`).

## Démarrage rapide

```bash
# 1. Compiler
mvn -B clean compile

# 2. Lancer les 49 tests de la couche de vérification
mvn -B test

# 3. Exécuter la démonstration (les deux scénarios du TP)
mvn -B exec:java
```

> **Important** — la démonstration écrit sur **deux flux** :
> * **`stdout`** : le déroulé des scénarios et l'état final ;
> * **`stderr`** : le message de conflit (`Transaction 2 : conflit détecté - ...`), car Hibernate journalise via `java.util.logging` et non via `System.out`.
>
> Pour capturer proprement les deux flux :
> ```bash
> mvn -B -q dependency:build-classpath -Dmdep.outputFile=cp.txt
> java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "target/classes;$(cat cp.txt)" \
>      com.example.OptimisticLockingApp > demo_out.txt 2> err.txt
> ```

## Structure du projet

```
tp6-jpa-optimistic-locking/
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/example
    │   │   ├── OptimisticLockingApp.java              # point d'entrée (exec.mainClass)
    │   │   ├── ConcurrentReservationSimulator.java    # scénario 1 : conflit NON géré
    │   │   ├── ConcurrentReservationWithRetry.java    # scénario 2 : conflit géré par retry
    │   │   ├── ReservationRetryHandler.java           # stratégie de retry (recharge + réapplique)
    │   │   ├── ResultatSimulation.java                # résultat observable d'une simulation
    │   │   ├── model/Reservation.java                 # @Entity + @Version
    │   │   ├── service/ReservationService.java        # contrat du service
    │   │   ├── service/ReservationServiceImpl.java    # CRUD + merge (verrouillage optimiste)
    │   │   └── util
    │   │       ├── JpaUtil.java                       # EntityManagerFactory + EntityManager
    │   │       └── Formats.java                       # formatage des dates et des réservations
    │   └── resources/META-INF/persistence.xml         # unité de persistance « reservationPU »
    └── test/java/com/example
        ├── AbstractJpaTest.java                       # base commune (EMF partagé, nettoyage)
        ├── Capture.java                               # capture de stdout/stderr
        ├── ReservationVersionTest.java                # 8 tests — sémantique de @Version
        ├── ReservationServiceTest.java                # 12 tests — CRUD et merge
        ├── ReservationRetryHandlerTest.java           # 9 tests — retry, épuisement, ligne supprimée
        ├── ConcurrentReservationSimulatorTest.java    # 7 tests — scénario 1
        ├── ConcurrentReservationWithRetryTest.java    # 7 tests — scénario 2
        └── OptimisticLockingAppTest.java              # 6 tests — vérification bout en bout
```

## Dépendances

| Dépendance | Version | Portée | Rôle |
|---|---|---|---|
| `javax.persistence:javax.persistence-api` | 2.2 | compile | API JPA (`@Entity`, `@Version`, `EntityManager`) |
| `org.hibernate:hibernate-core` | 5.6.15.Final | compile | Implémentation JPA / verrouillage optimiste |
| `net.bytebuddy:byte-buddy` | 1.14.18 | compile | Génération de proxies compatible JDK 21 |
| `com.h2database:h2` | 2.1.214 | runtime | Base embarquée en mémoire |
| `junit:junit` | 4.13.2 | test | Tests unitaires et d'intégration |

> **Note sur les versions** — le lab prescrit `hibernate-core:5.4.30.Final` et `h2:1.4.200` (versions de l'écosystème Java 8). Ces versions embarquent un *Byte Buddy* qui ne sait pas lire les fichiers de classe du JDK 21 et échouent au démarrage de l'`EntityManagerFactory` avec `Unsupported class file major version 65`. Le projet utilise donc Hibernate 5.6.15.Final (même API `javax.persistence`, même comportement de `@Version`) avec un Byte Buddy explicite, et `maven.compiler.release=11`. Le code du lab reste inchangé.

## Configuration JPA

`src/main/resources/META-INF/persistence.xml` — unité de persistance `reservationPU` (nom prescrit par le lab) :

```xml
<persistence-unit name="reservationPU" transaction-type="RESOURCE_LOCAL">
    <provider>org.hibernate.jpa.HibernatePersistenceProvider</provider>
    <class>com.example.model.Reservation</class>
    <properties>
        <property name="javax.persistence.jdbc.driver" value="org.h2.Driver"/>
        <property name="javax.persistence.jdbc.url" value="jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"/>
        <property name="hibernate.dialect" value="org.hibernate.dialect.H2Dialect"/>
        <property name="hibernate.hbm2ddl.auto" value="update"/>
        <property name="hibernate.show_sql" value="true"/>
        <property name="hibernate.format_sql" value="true"/>
    </properties>
</persistence-unit>
```

## Points clés du TP

### 1. `@Version` : la colonne qui trahit les conflits

```java
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private int version;          // pas de setter : c'est Hibernate qui pilote la version
    ...
}
```

À chaque `UPDATE` réel, Hibernate :

* incrémente la colonne `version` ;
* ajoute un **prédicat de version** à la clause `WHERE`.

```sql
update reservations
   set dateDebut=?, dateFin=?, salle=?, version=?
 where id=? and version=?
```

Si `version=?` ne correspond plus (la ligne a été modifiée entre la lecture et l'écriture), **0 ligne est mise à jour** : Hibernate lève `StaleObjectStateException`, que JPA ré-expose en `OptimisticLockException`.

### 2. Détacher puis fusionner : le cycle lecture → copie → `merge`

Le scénario repose sur une copie **détachée** de l'entité (chargée dans un premier `EntityManager`, celui-ci refermé) :

```
Transaction 1 et 2 : em.find(...)  →  version = 0 (les deux ont lu la même version)
Transaction 1      : merge(copie)  →  UPDATE ... where id=? and version=0  →  version = 1
Transaction 2      : merge(copie)  →  UPDATE ... where id=? and version=0  →  0 ligne
                                      → OptimisticLockException
```

### 3. `merge` n'écrit que si quelque chose change

Point de sémantique important, vérifié par les tests : `EntityManager.merge()` ne déclenche un `UPDATE` que si **au moins un champ diffère** de l'instantané chargé. Fusionner une copie strictement identique n'émet **aucun** `UPDATE`, n'incrémente donc **pas** la version, et ne provoque **pas** de conflit. Le test `uneFusionSansChangementNIncrementePasLaVersion` verrouille ce comportement.

Corollaire pratique : la version d'une entité fusionnée n'est incrémentée qu'**au flush**, c'est-à-dire lors du `commit()`. L'instance retournée par `merge` est managée et porte la nouvelle version **après** le commit — inutile de relire la ligne.

### 4. La stratégie de retry : recharger, puis **réappliquer l'intention**

Un retry correct ne se contente pas de refaire `merge` sur la même copie périmée (elle échouerait indéfiniment). Il faut :

1. **recharger** la ligne depuis la base (nouvelle version, nouvelles données) ;
2. **réappliquer l'intention métier** de la transaction (la salle et les dates qu'elle *voulait* écrire) sur l'instance fraîche ;
3. recommencer la transaction.

```java
// ReservationRetryHandler#modifierAvecRetry (extrait)
String salleVoulue = reservation.getSalle();          // l'intention, mémorisée AVANT la boucle
LocalDateTime debutVoulu = reservation.getDateDebut();
LocalDateTime finVoulue = reservation.getDateFin();

for (int tentative = 1; tentative <= MAX_RETRIES; tentative++) {
    try {
        return service.modifierReservation(reservation);
    } catch (OptimisticLockException e) {
        dernierNombreDeRetries = tentative;
        journal.println("Conflit détecté, tentative de retry " + tentative);
        Reservation fraiche = service.trouverParId(id);   // 1. recharger
        if (fraiche == null) {
            throw new OptimisticLockException("Réservation " + id + " supprimée entre-temps", e);
        }
        fraiche.setSalle(salleVoulue);                    // 2. réappliquer l'intention
        fraiche.setDateDebut(debutVoulu);
        fraiche.setDateFin(finVoulue);
        reservation = fraiche;                            // 3. retenter avec l'instance à jour
    }
}
throw new OptimisticLockException("Échec après " + MAX_RETRIES + " tentatives");
```

### 5. Bonnes pratiques retenues

* Toujours déclarer `@Version` sur les entités modifiables concurremment (coût : une colonne `integer`).
* Ne jamais construire un écran « lire, puis écrire plus tard » sans reporter la version lue jusqu'au `merge`.
* Réserver le **verrouillage pessimiste** (`LockModeType.PESSIMISTIC_WRITE`) aux cas réellement conflictuels ; l'optimiste est le défaut pour les réservations.
* Un retry doit être **borné** (`MAX_RETRIES`) et journalisé : un conflit non détecté est un bug, un conflit non géré est une perte de données.

## Tests

```bash
mvn -B test
```

```
Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Classe de test | Tests | Couverture |
|---|---|---|
| `ReservationVersionTest` | 8 | `@Version` initialisé à 0, incrémenté à chaque modification réelle, progression sur trois modifications, **pas** d'incrément sur une fusion sans changement, message exact d'Hibernate |
| `ReservationServiceTest` | 12 | `creer`, `trouverParId` (présent/absent), `listerToutes`, `modifierReservation` (champ modifié, entité inconnue, version périmée), `supprimer`, cycle complet |
| `ReservationRetryHandlerTest` | 9 | succès immédiat, succès après 1 retry, épuisement des 3 tentatives, ligne supprimée pendant le retry, réapplication effective de l'intention, compteur `getDernierNombreDeRetries()`, entrées invalides |
| `ConcurrentReservationSimulatorTest` | 7 | scénario 1 : les deux transactions lisent `version=0`, la 2ᵉ modification est perdue, état final, `ResultatSimulation`, sorties stdout/stderr |
| `ConcurrentReservationWithRetryTest` | 7 | scénario 2 : succès après retry, `version=2` et `salle=Salle C`, `tentatives=1`, `conflitDetecte=false`, chemin d'échec sur `stderr` |
| `OptimisticLockingAppTest` | 6 | exécution complète de `main` : présence des deux scénarios, `Conflit détecté, tentative de retry 1`, `Transaction 2 : modification réussie après retry.`, état final, absence de message d'échec |

## Résultat de la démonstration

Sortie standard (`stdout`) — la colonne `version` et le prédicat `and version=?` sont les points à observer :

```
=== 1. Conflit sans gestion : la modification de la transaction 2 est perdue ===
Hibernate: 
    
    create table reservations (
       id bigint generated by default as identity,
        dateDebut timestamp not null,
        dateFin timestamp not null,
        salle varchar(255) not null,
        version integer not null,
        primary key (id)
    )
Hibernate: 
    delete 
    from
        reservations
Hibernate: 
    ALTER TABLE reservations ALTER COLUMN id RESTART WITH 1
Hibernate: 
    insert 
    into
        reservations
        (id, dateDebut, dateFin, salle, version) 
    values
        (default, ?, ?, ?, ?)
Réservation créée : id=1, salle=Salle A, dateDebut=17/10/2026 20:57, dateFin=17/10/2026 22:57, version=0

Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Transaction 1 a lu : version=0
Transaction 2 a lu : version=0

Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Hibernate: 
    update
        reservations 
    set
        dateDebut=?,
        dateFin=?,
        salle=?,
        version=? 
    where
        id=? 
        and version=?
Transaction 1 : modification réussie.
Nouvelle version en base : 1

Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?

État final : id=1, salle=Salle B, dateDebut=17/10/2026 20:57, dateFin=17/10/2026 22:57, version=1

=== 2. Conflit géré par retry : la transaction 2 recharge et réapplique son intention ===
Hibernate: 
    delete 
    from
        reservations
Hibernate: 
    ALTER TABLE reservations ALTER COLUMN id RESTART WITH 1
Hibernate: 
    insert 
    into
        reservations
        (id, dateDebut, dateFin, salle, version) 
    values
        (default, ?, ?, ?, ?)
Réservation créée : id=1, salle=Salle A, dateDebut=18/10/2026 20:57, dateFin=18/10/2026 22:57, version=0

Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Hibernate: 
    update
        reservations 
    set
        dateDebut=?,
        dateFin=?,
        salle=?,
        version=? 
    where
        id=? 
        and version=?
Transaction 1 : modification réussie.

Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Conflit détecté, tentative de retry 1
Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?
Hibernate: 
    update
        reservations 
    set
        dateDebut=?,
        dateFin=?,
        salle=?,
        version=? 
    where
        id=? 
        and version=?
Transaction 2 : modification réussie après retry.
Hibernate: 
    select
        reservatio0_.id as id1_0_0_,
        reservatio0_.dateDebut as datedebu2_0_0_,
        reservatio0_.dateFin as datefin3_0_0_,
        reservatio0_.salle as salle4_0_0_,
        reservatio0_.version as version5_0_0_ 
    from
        reservations reservatio0_ 
    where
        reservatio0_.id=?

État final : id=1, salle=Salle C, dateDebut=18/10/2026 20:57, dateFin=18/10/2026 22:57, version=2

=== État final de la base ===
Hibernate: 
    select
        reservatio0_.id as id1_0_,
        reservatio0_.dateDebut as datedebu2_0_,
        reservatio0_.dateFin as datefin3_0_,
        reservatio0_.salle as salle4_0_,
        reservatio0_.version as version5_0_ 
    from
        reservations reservatio0_ 
    order by
        reservatio0_.id
  id=1, salle=Salle C, dateDebut=18/10/2026 20:57, dateFin=18/10/2026 22:57, version=2
  total = 1 réservation(s)
```

Sortie d'erreur (`stderr`) — c'est ici qu'arrive le message de conflit, émis par Hibernate :

```
Oct 16, 2026 6:57:16 PM org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl configure
WARN: HHH10001002: Using Hibernate built-in connection pool (not for production use!)
Transaction 2 : conflit détecté - Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect) : [com.example.model.Reservation#1]
```

### Lecture des résultats

| Observation | Interprétation |
|---|---|
| `Transaction 1 a lu : version=0` / `Transaction 2 a lu : version=0` | Les deux transactions ont lu **la même** version : elles sont en conflit potentiel. |
| Scénario 1 : `set ... version=?` puis `État final : salle=Salle B, version=1` | La modification de la transaction 2 (Salle C) a été **silencieusement perdue**. |
| Scénario 2 : `Conflit détecté, tentative de retry 1` | Hibernate a détecté le conflit : 0 ligne mise à jour par le prédicat de version. |
| Scénario 2 : `Transaction 2 : modification réussie après retry.` | Après rechargement et réapplication de l'intention, la transaction 2 a réussi. |
| `État final : salle=Salle C, version=2` | Aucune mise à jour perdue : deux écritures réelles, deux incréments de version. |

## Écarts assumés

| # | Écart | Spécification du lab | Implémentation | Justification |
|---|---|---|---|---|
| 1 | Version d'Hibernate | `5.4.30.Final` | `5.6.15.Final` | Le Byte Buddy embarqué ne lit pas les *class files* du JDK 21 (`Unsupported class file major version 65`). Même API `javax.persistence`, même sémantique de `@Version`. |
| 2 | Version de H2 | `1.4.200` | `2.1.214` | Compatible JDK 21 ; `ALTER TABLE ... ALTER COLUMN id RESTART WITH` y est stable. |
| 3 | Byte Buddy | implicite | `net.bytebuddy:byte-buddy:1.14.18` explicite | Force une version compatible JDK 21 pour la génération de proxies. |
| 4 | Niveau de langage | Java 8 (`source`/`target`) | `maven.compiler.release=11` | Compatible avec les JDK modernes sans avertissement de *bootstrap classpath*. |
| 5 | Concurrence | Deux transactions « concurrentes » | Simulation **séquentielle** de deux copies détachées | C'est exactement ce que fait le code prescrit (deux `find` détachés puis deux `update`) ; le rendre réellement multithread introduirait un test non déterministe. |
| 6 | `modifierReservation` | Copie des champs sur l'instance **managée** retournée par `find` | `find` = garde d'existence, puis `em.merge(copieDétachée)` | **Bug de spécification** : en copiant sur l'instance managée, celle-ci porte toujours la version courante et aucun conflit ne peut jamais survenir — l'`OptimisticLockException` promise par le TP est inatteignable. |
| 7 | `ReservationRetryHandler` | `reservation.setSalle(reservation.getSalle())` | Mémorisation puis réapplication de l'intention | **Bug de spécification** : l'affectation prescrite est un **no-op**, le retry ne réapplique donc jamais la modification voulue. |
| 8 | Simulateur (scénario 1) | 2ᵉ `merge` sans garde | Garde d'existence (`IllegalArgumentException` explicite) | Une ligne supprimée entre-temps produirait sinon un `INSERT` accidentel (identifiant détaché) au lieu d'une erreur claire. |
| 9 | Journalisation | Sortie Hibernate complète | `org.hibernate` limité à `WARNING` dans `main` | Hibernate 5.6 journalise via `java.util.logging` : les lignes INFO de démarrage noient le message de conflit sur `stderr`. La sortie utile reste visible. |
| 10 | Observabilité du retry | Nombre de tentatives non exposé | `ReservationRetryHandler#getDernierNombreDeRetries()` | Permet d'asserter le comportement de retry de façon déterministe ; n'altère pas la sortie de la démonstration. |
| 11 | Couche de tests | Non demandée | 49 tests JUnit 4 + `AbstractJpaTest` + `Capture` | Vérification automatisée des deux scénarios et de la sémantique de `@Version`. |
| 12 | `show_sql` | `true` | `true` | Conservé tel quel : les requêtes SQL font partie de la démonstration pédagogique. |

## Auteur

**Mohamed Dani** — [@danimohamed](https://github.com/danimohamed)

Travail réalisé dans le cadre du cours *JPA / Hibernate* (MLIAEdu, cours 28 — lab 149).
