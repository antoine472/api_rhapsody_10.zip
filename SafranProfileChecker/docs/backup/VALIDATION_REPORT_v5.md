# Rapport de validation - corrections SafranChecker v5

**Date :** 2026-07-24  
**Base :** `SafranProfileChecker.zip` fourni par l'utilisateur  
**Version du générateur de référence :** v20, non modifiée dans ce lot

## Fichiers Java modifiés

- `src/main/java/com/safran/checker/service/CheckerRuleService.java`
  - `modificationCounter = 5`
- `src/main/java/com/safran/checker/service/RhapsodyModelQueries.java`
  - `modificationCounter = 3`

## Configuration modifiée

- `config/safran-checkers.xml`
  - descriptions alignées avec les règles consolidées ;
  - checker 206492 configuré en `Warning` ;
  - règles de représentation, With Reference, Use Case et terminaison précisées.

## Corrections métier principales

- directions des Flow Ports au même niveau et en hiérarchie parent/enfant ;
- famille Functional Flow ou Logical Flow selon le type d'enfant ;
- exception de sous-Flow Item pour Logical System parent vers Function enfant ;
- stéréotypes exclusifs cohérents lorsque les deux extrémités fonctionnelles appartiennent à des Functions ou Functions With Reference ;
- connectivité sémantique des Operational Flows, couverture par au moins une vue et détection des doublons par paire de ports ;
- SystemOfInterest appliqué à la lifeline et à l'Operational System associé ;
- redéfinition complète des ports With Reference en fonction du nombre de références dans tout le projet ;
- représentation explicite des Logical Systems dans un Logical Breakdown Structure ;
- orientation et types des relations Use Case ;
- représentation des Lifecycle Phases et Operating Modes uniquement dans leur arbre de propriété autorisé, sans accepter une représentation invalide supplémentaire ;
- Operating Mode sans transition ou à sens unique traité en warning ;
- TerminationState facultatif mais atteignable lorsqu'il existe ;
- comparaison des noms de ports sans casse, espaces ni caractères `_` ;
- pseudostate initial natif accepté même lorsqu’il n’est pas la seule représentation graphique du chart ;
- usage d’un Flow Item limité au typage d’un Flow Port ou à son transport par un flow ;
- diagnostics contrôlés et dédupliqués sur les exceptions API structurantes.

## Vérification technique réalisée

Les deux classes Java modifiées ont été compilées avec `javac` contre des stubs reproduisant les signatures Rhapsody utilisées et les classes existantes du projet : aucune erreur de syntaxe ou de typage Java n'a été détectée. Une compilation complète de toutes les classes n'a pas pu être réalisée dans le conteneur, car les stubs ne couvrent pas `IRPExternalCheckRegistry`, `RPExternalCheck`, `RPUserPlugin` et `IRPApplication`.

Cette vérification ne remplace pas :

- la compilation complète dans Eclipse avec le vrai `rhapsody.jar` 10.0.2 ;
- l'exécution runtime dans Rhapsody ;
- le passage des 198 scénarios décrits dans la nouvelle spécification. Tous ces scénarios possèdent désormais un résultat attendu explicite : 83 OK, 108 NOK, 3 WARNING et 4 MIXTE.

## Documents produits

- `Specification_implementation_checkers_Safran_v5.xlsx`
- `Propositions_nouveaux_checkers_Safran.md`
- `Synthese_explication_Profile_Safran.docx`
- `Guide_operateur_construction_modeles_test_checkers.docx`
- `rhapsody_expert.md`

## Binaries

Le répertoire `bin` repris du projet source n'a pas été reconstruit. Les livrables de référence de ce lot sont les sources Java et la configuration XML.
