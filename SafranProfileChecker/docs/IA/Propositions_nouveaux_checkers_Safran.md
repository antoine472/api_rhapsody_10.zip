# Propositions de nouveaux checkers Safran

**Date :** 2026-07-24  
**Statut :** propositions à soumettre à la liste officielle avant implémentation  
**Règle d’architecture :** aucun checker de cette liste ne doit être codé avant attribution d’un identifiant officiel et ajout dans `safran-checkers.xml`.

## 1. LifecycleEndPhaseReachabilityChecker

### Identifiant

À attribuer par le processus officiel.

### Sévérité proposée

`Warning` par défaut. Une sévérité `Error` peut être décidée si l’inaccessibilité d’une End Phase est considérée comme une incohérence bloquante de définition du Lifecycle.

### Métaclasses proposées

- `Lifecycle`
- ou `End Phase`, selon le mode d’appel retenu par Rhapsody.

### Règle métier

Une `End Phase` est facultative. Lorsqu’elle existe, elle doit être atteignable depuis au moins une `Start Phase` en suivant uniquement des `Lifecycle Phase Transition` appartenant au même Lifecycle.

Le checker doit :

1. identifier les `Start Phase`, `Lifecycle Phase`, `End Phase` et `Lifecycle Phase Transition` du Lifecycle ;
2. construire un graphe orienté à partir du sens `dependent -> dependsOn` défini pour les transitions du Profile ;
3. parcourir le graphe depuis toutes les `Start Phase` ;
4. signaler chaque `End Phase` présente mais non atteignable ;
5. accepter un Lifecycle sans `End Phase`.

### Scénarios d’approbation

| Scénario | Attendu |
|---|---|
| Lifecycle sans End Phase | OK |
| Une End Phase atteignable depuis une Start Phase | OK |
| End Phase sans transition entrante | NOK |
| End Phase située dans une branche déconnectée | NOK |
| Plusieurs End Phases toutes atteignables | OK |
| Plusieurs End Phases dont une inaccessible | NOK |
| Cycle de phases sans chemin vers l’End Phase | NOK |
| Transition vers une End Phase d’un autre Lifecycle | NOK |

### Correction opérateur

Créer ou réorienter les `Lifecycle Phase Transition` afin qu’un chemin partant d’une `Start Phase` atteigne chaque `End Phase` du même Lifecycle.

---

## 2. ExclusiveStereotypeMultiplicityChecker

### Identifiant

À attribuer par le processus officiel.

### Sévérité proposée

`Error`.

### Métaclasses proposées

Toutes les métaclasses pouvant recevoir des stéréotypes Safran. Si Rhapsody ne permet pas une déclaration générique satisfaisante, la configuration XML devra énumérer les métaclasses réellement concernées.

### Règle métier

Un élément peut porter plusieurs stéréotypes non exclusifs. En revanche, si un stéréotype appliqué possède le tag ou la propriété `Exclusive = Yes`, l’élément ne peut porter qu’un seul stéréotype dont `Exclusive = Yes`.

Cette règle est indépendante du checker `208311_UnclassifiedGenericFlowChecker` :

- `208311` vérifie qu’un `Generic Flow` ne reste pas générique dans le modèle final ;
- le nouveau checker vérifie l’exclusivité de tous les stéréotypes appliqués à tout élément concerné.

### Scénarios d’approbation

| Scénario | Attendu |
|---|---|
| Aucun stéréotype appliqué | OK |
| Plusieurs stéréotypes avec `Exclusive = No` | OK |
| Un stéréotype `Exclusive = Yes` et plusieurs non exclusifs | OK |
| Deux stéréotypes `Exclusive = Yes` | NOK |
| Un stéréotype sans tag `Exclusive` et un stéréotype exclusif | OK |
| Valeur `yes`, `Yes`, `TRUE` ou `1` | Interprétée comme exclusive |
| Impossible de lire le tag `Exclusive` | Diagnostic contrôlé, pas d’exception silencieuse |

### Correction opérateur

Conserver un seul stéréotype exclusif et retirer ou remplacer les autres stéréotypes exclusifs. Les stéréotypes non exclusifs peuvent être conservés.

---

## 3. OperationalSystemOfInterestUniquenessChecker

### Identifiant

À attribuer par le processus officiel.

### Sévérité proposée

`Error`.

### Métaclasses proposées

- `Operational System`
- éventuellement `A - Operational Analysis Package` pour un contrôle agrégé unique.

### Règle métier

Dans une vision opérationnelle, un seul `Operational System` du périmètre de modèle doit porter le stéréotype `SystemOfInterest`.

Ce checker compléterait :

- `207822`, qui contrôle l’affectation cohérente du System Of Interest dans chaque Operational Sequence Diagram ;
- `206483`, qui distingue le System Of Interest des Operational Systems externes dans les associations de Use Case.

### Scénarios d’approbation

| Scénario | Attendu |
|---|---|
| Aucun Operational System marqué SystemOfInterest | NOK |
| Exactement un Operational System marqué | OK |
| Deux Operational Systems marqués | NOK |
| Stéréotype porté uniquement par une lifeline | NOK au niveau du modèle, indépendamment de 207822 |
| Operational System externe sans stéréotype | OK |

### Correction opérateur

Appliquer `SystemOfInterest` à l’unique Operational System correspondant au système étudié et retirer ce stéréotype des systèmes externes.

---

## Processus d’implémentation après approbation

Pour chaque proposition approuvée :

1. attribuer l’identifiant officiel ;
2. créer une entrée dans `safran-checkers.xml` avec domaine, nom, sévérité, métaclasses et activation ;
3. créer la classe Java dans le package `checkers`, héritant de `SafranChecker` ;
4. ajouter la règle métier dans `CheckerRuleService` ;
5. ajouter les fonctions d’accès modèle réutilisables dans `RhapsodyModelQueries` si nécessaire ;
6. créer les scénarios OK, NOK et à la marge définis ci-dessus ;
7. exécuter le checker dans Rhapsody 10.0.2 ;
8. vérifier l’absence de faux positifs, puis corriger chaque scénario NOK et relancer le checker.
