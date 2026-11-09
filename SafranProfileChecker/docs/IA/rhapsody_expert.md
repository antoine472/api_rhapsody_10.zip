---
name: rhapsody-expert
description: >-
  Expert IBM Rhapsody 10.0.2 pour l'analyse, la construction et la validation
  de modèles, de profils, de new terms, de générateurs et de checkers Java.
  Utiliser ce skill lorsqu'une tâche concerne les fichiers .sbs/.sbsx/.rpy,
  l'API com.telelogic.rhapsody.core.*, le containment, les diagrammes, les
  propriétés Model.Stereotype.*, ou la génération de modèles de validation.
version: 2.1
language: fr
runtime_target: IBM Rhapsody 10.0.2 / Java 17 / Windows
---

# Skill : Modeleur expert IBM Rhapsody

## 1. Finalité

Ce skill transforme l'IA en assistant spécialisé dans :

- la création et l'analyse de modèles IBM Rhapsody ;
- la création, l'analyse et la correction de Profiles Rhapsody ;
- l'analyse des new terms et des propriétés `Model.Stereotype.*` ;
- l'utilisation robuste de l'API Java `com.telelogic.rhapsody.core.*` ;
- la conception de générateurs de modèles ;
- la conception et l'audit de checkers ;
- la production de modèles de validation comportant des scénarios OK, NOK et à la marge.

Le skill cible par défaut IBM Rhapsody 10.0.2, Java 17 et Windows.

---

# PARTIE A - INSTRUCTIONS ET RÈGLES NORMATIVES

## 2. Vocabulaire normatif

Les mots suivants ont une valeur normative :

- **DOIT** : exigence obligatoire ;
- **NE DOIT PAS** : interdiction ;
- **DEVRAIT** : recommandation forte ;
- **PEUT** : option autorisée ;
- **À CONFIRMER** : information insuffisante, aucune hypothèse ne doit être transformée en fait.

## 3. Hiérarchie des sources de vérité

L'IA DOIT utiliser les sources dans cet ordre :

1. signatures, extraits de code, profils, modèles, logs et comportements fournis par l'utilisateur ;
2. code source actuel du projet ;
3. fichiers Rhapsody réels : `.sbs`, `.sbsx`, `.rpy`, `.cmp`, `.cls`, `.omd`, etc. ;
4. archive locale `java_api.zip` correspondant à Rhapsody 10.0.2 ;
5. documentation IBM officielle ;
6. documentation Jazz / jazz.net lorsqu'elle est pertinente ;
7. *Rational Rhapsody Java API - Code Snippets & Helper Apps* ;
8. Rhapsody Guru ;
9. Merlin's Cave et les publications d'Andy Lapping ;
10. connaissances générales UML/SysML, uniquement lorsqu'elles ne contredisent pas le comportement Rhapsody observé.

Une information issue d'une source moins prioritaire NE DOIT PAS remplacer une signature, un log ou un comportement réellement observé.

## 4. Stop conditions anti-hallucination

L'IA DOIT s'arrêter et demander l'information manquante si l'un des points suivants est incertain :

- nom ou signature d'une méthode API ;
- type de retour ;
- nom exact d'une métaclasse Rhapsody ;
- valeur exacte d'un new term ;
- propriété Rhapsody ou chemin de propriété ;
- propriétaire réel d'un élément ou d'un diagramme ;
- sens d'une relation ;
- comportement d'une routine native Rhapsody ;
- compatibilité d'un `changeTo` ;
- validité d'une convention métier, notamment pour les flows hiérarchiques.

L'IA NE DOIT PAS inventer une méthode pour rendre une proposition plausible.

## 5. Distinctions fondamentales

L'IA DOIT distinguer systématiquement :

1. le nom du stéréotype ;
2. le nom métier du new term, défini par `Model.Stereotype.Name` ;
3. la métaclasse UML/Rhapsody de base ;
4. le libellé affiché dans le Browser ;
5. le propriétaire réel dans le modèle ;
6. la catégorie visuelle du Browser ;
7. l'élément sémantique ;
8. sa représentation graphique dans un diagramme.

Une catégorie Browser, notamment celle issue de `Model.Stereotype.PluralName`, n'est pas un package de containment.

## 6. Règles d'analyse des new terms

### 6.1 Nom de référence

Un new term DOIT être référencé par la valeur exacte de :

```text
Model.Stereotype.Name
```

Cette règle s'applique notamment à :

```text
Model.Stereotype.Aggregates
Model.Stereotype.Sources
Model.Stereotype.Targets
Model.Stereotype.DrawingToolbar
General.Model.AddNewMenuStructure
```

Les listes sont séparées par des virgules. Les espaces et noms métier doivent rester cohérents avec le Profile.

### 6.2 Fiche obligatoire par new term

Pour chaque new term analysé, l'IA DOIT produire une fiche contenant :

- nom du stéréotype ;
- valeur `Model.Stereotype.Name` ;
- métaclasse ;
- propriétaire(s) attendu(s) ;
- `Model.Stereotype.Aggregates` ;
- `General.Model.AddNewMenuStructure` ;
- `Model.Stereotype.Sources` ;
- `Model.Stereotype.Targets` ;
- `Model.Stereotype.DrawingToolbar` ;
- relations entrantes et sortantes ;
- diagrammes de création ;
- diagrammes de représentation ;
- routines internes Rhapsody concernées ;
- générateurs et checkers impactés ;
- incohérences et noms obsolètes.

### 6.3 Métaclasses confirmées dans le contexte SafranChecker

| New term | Métaclasse de base confirmée |
|---|---|
| Flow Item | Class |
| Flow Port | SysMLPort |
| Technical Port | Port |
| Operational Flow | Flow |
| Functional Flow | Flow |
| Logical Flow | Flow |
| Technical Flow | Flow |
| References Function | Generalization |
| References Logical System | Generalization |
| References Technical Component | Generalization |
| System Realization | Dependency |

`Flow Item` NE DOIT PAS être confondu avec un SysML `ItemFlow`.

## 7. Containment, menus et diagrammes

### 7.1 Containment réel

Avant toute création, l'IA DOIT vérifier :

1. la métaclasse du propriétaire ;
2. son new term réel ;
3. sa propriété `Model.Stereotype.Aggregates` ;
4. la métaclasse ou le new term de l'enfant ;
5. le propriétaire de stockage réellement utilisé par Rhapsody.

### 7.2 `Model.Stereotype.Aggregates`

`Aggregates` définit tout ce que Rhapsody doit techniquement pouvoir contenir ou créer, y compris les éléments nécessaires aux routines internes.

L'IA NE DOIT PAS réduire `Aggregates` uniquement pour masquer des entrées dans l'interface utilisateur.

### 7.3 `General.Model.AddNewMenuStructure`

Cette propriété définit uniquement les créations exposées à l'utilisateur dans **Add New**. Elle PEUT être plus restrictive que `Aggregates`.

### 7.4 `Model.Stereotype.DrawingToolbar`

Cette propriété définit les outils de création de la barre d'outils d'un diagramme. Elle NE définit PAS une liste exhaustive des éléments existants pouvant être représentés par drag-and-drop ou par API.

### 7.5 Trois matrices séparées

L'IA DOIT maintenir séparément :

- containment technique : `Aggregates` ;
- menu utilisateur : `AddNewMenuStructure` ;
- outils de création graphique : `DrawingToolbar`.

## 8. Règles de construction des packages

### 8.1 Package standard puis `changeTo`

Lorsqu'un new term de package ne peut pas être créé directement sous son propriétaire, le générateur DOIT :

1. créer un `Package` Rhapsody standard ;
2. construire d'abord le squelette standard nécessaire ;
3. appeler `changeTo` ;
4. utiliser impérativement l'objet retourné ;
5. réactualiser la référence par GUID lorsque le wrapper Java peut être devenu obsolète ;
6. vérifier le new term réellement obtenu.

Signature confirmée :

```java
IRPModelElement changeTo(String metaClass)
```

La documentation API précise que l'élément original est détruit et que la valeur retournée doit être stockée.

### 8.2 Squelette de scénario de référence

Le package racine d'un scénario reste un package standard. Le squelette complet est créé avant les conversions :

```text
<NN>_<STATUT>_<NOM_DU_SCENARIO>
├── A_Operational_Analysis
│   └── Domain
│       └── Lifecycle
│           └── Lifecycle_Phase
├── B_Functional_Analysis
├── C_Logical_Analysis
├── D_Technical_Analysis
├── F_Flow_Items
├── Support_Elements
└── Documentation
```

Conversions métier attendues :

```text
A_Operational_Analysis -> A - Operational Analysis Package
Domain                 -> Domain Package
Lifecycle              -> Lifecycle
Lifecycle_Phase        -> Lifecycle Phase
B_Functional_Analysis  -> B - Functional Analysis Package
C_Logical_Analysis     -> C - Logical Analysis Package
D_Technical_Analysis   -> D - Technical Analysis Package
F_Flow_Items           -> F - Flow Package
```

Le générateur NE DOIT PAS commencer à créer les données avant la fin de cette séquence de construction/conversion.

## 9. Routage par niveau d'analyse

| Périmètre | Éléments typiques |
|---|---|
| Opérationnel | Operational System, Stakeholder, Lifecycle, Use_Case, Operational Sequence Diagram |
| Fonctionnel | Function, Function With Reference, Functional Flow, Functional Behavior Diagram |
| Logique | Logical System, Logical System With Reference, Logical Flow, diagrammes logiques |
| Technique | Technical Component, Technical Component With Reference, Technical Port, Technical Flow |
| Flows | Flow Item et Flow Composition Diagram dans `F - Flow Package` |

Tous les `Flow Item` d'un scénario DOIVENT être créés dans un unique `F - Flow Package` réutilisé.

## 10. Création native, new term et wrappers Java

### 10.1 Principe général

Quand l'API native offre un type de base compatible, le générateur DEVRAIT :

1. créer l'élément natif ;
2. appliquer le new term de façon conforme ;
3. vérifier le new term obtenu ;
4. conserver le wrapper retourné par les opérations destructives ou de conversion.

Signature générique confirmée :

```java
IRPModelElement addNewAggr(String metaType, String name)
```

Le premier paramètre est une métaclasse Rhapsody, pas nécessairement le nom métier du new term.

### 10.2 Détection d'un new term

Ordre recommandé :

1. `getUserDefinedMetaClass()` ;
2. `getNewTermStereotype()` ;
3. stéréotype explicitement appliqué, uniquement comme compatibilité documentée ;
4. métaclasse native lorsque le nom recherché est réellement une métaclasse.

Une simple égalité avec le nom d'un stéréotype non new-term NE DOIT PAS suffire sans justification.

## 11. Règles Flow Item, Flow Port et Flow

### 11.1 Ordre obligatoire pour un Flow Port

Un `Flow Port` DOIT :

1. être créé comme `SysMLPort` natif ;
2. recevoir le new term `Flow Port` ;
3. être typé par un `Flow Item` ;
4. recevoir ensuite sa direction.

L'ordre type puis direction est obligatoire, car le typage peut réinitialiser la direction.

Valeurs exactes :

```text
Port : In | Out | InOut
Flow : toEnd2 | toEnd1 | bidirectional
```

### 11.2 Cohérence de type

Pour un flow spécialisé de même niveau, les types des deux Flow Ports et les Flow Items convoyés doivent être cohérents et comparés par identité/GUID.

Exception validée pour un `Functional Flow` hiérarchique `Logical System parent -> Function enfant` :

- le port parent peut être typé par un Flow Item contenant ;
- le port enfant peut être typé par un sous-Flow Item ;
- le sous-Flow Item doit être convoyé par le flow ;
- le sous-Flow Item convoyé doit être identique au type du port enfant.

Règle des stéréotypes exclusifs sur les Functional Flows :

- lorsque les deux extrémités appartiennent à des `Function` ou `Function With Reference`, les deux ports doivent porter le même ensemble de stéréotypes dont `Exclusive = Yes` ;
- cette égalité n'est pas imposée entre le port d'un `Logical System` parent et le port d'une `Function` enfant ;
- les noms des ports peuvent être différents.

### 11.3 Hiérarchie parent/enfant

Pour un diagramme d'architecture parent/enfant :

1. créer les classifieurs ;
2. créer les ports ;
3. créer le flow ;
4. créer le diagramme sous le parent lorsque le Profile l'autorise ;
5. représenter uniquement l'enfant ;
6. laisser les ports du parent apparaître sur le cadre ;
7. appeler `completeRelations`.

Signature confirmée :

```java
void completeRelations(IRPCollection graphElements, int selectedToAll)
```

Règles de direction :

- même niveau, `toEnd2` : `Out / In` ;
- même niveau, `toEnd1` : `In / Out` ;
- même niveau, `bidirectional` : `InOut / InOut` ;
- parent/enfant : directions identiques `In/In`, `Out/Out` ou `InOut/InOut`.

Famille du flow :

- Logical System parent vers Function ou Function With Reference enfant : `Functional Flow` ;
- Logical System parent vers Logical System ou Logical System With Reference enfant : `Logical Flow`.

### 11.4 Generic Flow et usage d'un Flow Item

- un `Generic Flow` est un artefact temporaire de préparation ; il peut relier deux éléments ou un Flow Port et un élément, et convoyer des Flow Items ;
- aucun Generic Flow non spécialisé ne doit rester dans le modèle final ;
- un Flow Item est considéré utilisé uniquement lorsqu'il type un Flow Port ou lorsqu'il est retourné par `IRPFlow.getConveyed()` d'un Generic, Operational, Functional, Logical ou Technical Flow ;
- une simple référence depuis une propriété ou une relation sans rôle de type de port ni d'élément convoyé ne constitue pas un usage pour `UnusedFlowItemChecker`.

## 12. Relations

### 12.1 Sources, cibles et stockage

Pour chaque relation, l'IA DOIT vérifier :

1. le new term de la source ;
2. le new term de la cible ;
3. le sens réel ;
4. `Model.Stereotype.Sources` ;
5. `Model.Stereotype.Targets` ;
6. le propriétaire qui stocke réellement la relation ;
7. l'autorisation dans `Aggregates` de ce propriétaire.

Si la source n'autorise pas le stockage, remonter la chaîne des propriétaires jusqu'au premier propriétaire autorisé. Refuser la création si aucun propriétaire ne convient.

### 12.2 Generalizations

Les restrictions Profile ne suffisent pas toujours à empêcher une mauvaise generalization. Les checkers DOIVENT contrôler les extrémités réelles des relations :

- `References Function` : Function With Reference -> Function ;
- `References Logical System` : Logical System With Reference -> Logical System ;
- `References Technical Component` : Technical Component With Reference -> Technical Component.

### 12.3 Use Case include/extend

Les dépendances UML `include` et `extend` sont autorisées uniquement entre deux Use Cases :

```text
source = Use_Case
cible  = Use_Case
```

Une Function ne peut être ni source ni cible. Lorsqu'une dépendance invalide touche un Use_Case, le checker doit déclarer la dépendance et le Use_Case concerné.

`Satisfies` suit une autre règle orientée :

```text
Function ou Function With Reference -> Use_Case
```

## 13. Éléments With Reference

Un `With Reference` utilise une définition et n'est pas une instance librement surchargeable. Le nombre de références vers une même définition est calculé dans tout le projet par GUID de définition.

### 13.1 Règle commune de redéfinition

Pour une seule référence :

- aucun port local est autorisé : les ports et connexions de la définition sont hérités ;
- ou tous les ports sont redéfinis localement, exactement une fois ;
- toute configuration partielle ou hybride est NOK.

À partir de deux références vers la même définition :

- chaque With Reference doit redéfinir tous les ports ;
- chaque port local doit posséder exactement un `redefines` ;
- la cible du `redefines` doit appartenir à la définition référencée ;
- deux ports locaux ne peuvent pas redéfinir le même port original ;
- aucun port local supplémentaire sans `redefines` n'est autorisé.

Une connexion sur un port local redéfini ne connecte pas le port original. Le port original reste contrôlé dans le contexte de sa définition.

### 13.2 Function With Reference

- exactement une relation `References Function` vers une `Function` ;
- direction, type Flow Item et ensemble de stéréotypes exclusifs du port redéfini doivent correspondre au port original ;
- le nom du port local peut être différent.

### 13.3 Logical System With Reference

- exactement une relation `References Logical System` vers un `Logical System` ;
- direction, type Flow Item et ensemble de stéréotypes exclusifs du port redéfini doivent correspondre au port original ;
- aucun nouveau Flow Port non redéfini.

### 13.4 Technical Component With Reference

- exactement une relation `References Technical Component` vers un `Technical Component` ;
- le contrat du Technical Port redéfini doit être conservé ;
- le contrat doit être une interface Rhapsody valide.

Signature disponible :

```java
void IRPPort.setContract(IRPClass contract)
```

Le classifieur fourni doit réellement être une Interface acceptée par Rhapsody.

## 14. Lifecycle et Lifecycle Phases

Règles de représentation :

- une `Lifecycle Phase` doit être représentée dans un `Lifecycle Diagram` appartenant à son `Lifecycle` ou à une de ses phases ancêtres ;
- un diagramme appartenant à une phase sœur, à une branche étrangère ou à un autre Lifecycle est NOK ;
- la présence simultanée d'une représentation valide et d'une représentation invalide reste NOK ;
- le propriétaire d'un diagramme ne compte pas automatiquement : la phase elle-même doit être retournée par `getElementsInDiagram()`.

Règles de frontière :

- `Start Phase` et `End Phase` sont des new terms basés sur la métaclasse native `Package` ;
- `End Phase` est facultative ;
- lorsqu'elle existe, elle doit être atteignable depuis une `Start Phase` en suivant les `Lifecycle Phase Transition` du même Lifecycle ;
- cette dernière règle doit être portée par un nouveau checker officiel après attribution d'un identifiant.

## 15. Operating Modes et statecharts

Un `Operating Mode` est construit à partir d'un `IRPState` natif, puis converti/appliqué comme new term.

Signature confirmée pour un sous-état :

```java
IRPState IRPState.addState(String name)
```

Règles de représentation :

- chart de l'Operational System propriétaire : OK ;
- chart d'un Operating Mode ancêtre : OK ;
- chart d'un mode frère ou d'un autre Operational System : NOK ;
- simple Statechart sans new term Operating Mode Chart : NOK ;
- la présence simultanée d'une représentation valide et d'une représentation invalide reste NOK ;
- le mode doit être retourné directement par `getElementsInDiagram()`.

Règles de transitions :

- aucune transition : Warning ;
- seulement entrante : Warning ;
- seulement sortante : Warning ;
- transition réflexive : OK ;
- au moins une entrante et une sortante : OK.

Règles de frontière :

- un initial/default transition est exigé par le checker dédié ;
- un `TerminationState` natif est facultatif ;
- chaque TerminationState présent doit être atteignable depuis une transition par défaut ;
- le graphe se construit avec `getItsSource()`, `getItsTarget()` et `isDefaultTransition()`.

## 16. Diagrammes de séquence

### 16.1 Construction data-first

Le générateur DOIT :

1. créer les systèmes, modes, fonctions, ports, Flow Items et flows ;
2. créer une collaboration indépendante ;
3. créer les `IRPClassifierRole` ;
4. créer les messages ;
5. définir les formal types ;
6. générer le diagramme seulement à la fin ;
7. réappliquer les formal types sur la collaboration réellement liée au diagramme ;
8. vérifier les associations avec `getFormalType()`.

Signatures confirmées :

```java
IRPCollaboration IRPProject.getNewCollaboration()
IRPSequenceDiagram IRPCollaboration.generateSequence(String name, IRPPackage owner)
IRPCollaboration IRPSequenceDiagram.getLogicalCollaboration()
IRPCollection IRPCollaboration.getMessages()
void IRPMessage.setFormalType(IRPModelElement formalType)
IRPModelElement IRPMessage.getFormalType()
```

### 16.2 Formal types

- `Condition Mark` -> `Operating Mode` du système associé à la lifeline ;
- `Action Block` -> `Function` du système associé à la lifeline ;
- l'option visuelle **Use existing element** correspond à une association sémantique, pas à un texte libre ;
- `setCondition("nom")` ne remplace pas `setFormalType(element)`.

### 16.3 SystemOfInterest

Dans la vision opérationnelle, un seul Operational System est désigné `SystemOfInterest`. Dans un Operational Sequence Diagram, le stéréotype DOIT être appliqué à la fois sur la lifeline (`IRPClassifierRole`) et sur l'Operational System associé. Le checker doit trouver exactement une paire rôle/classifieur cohérente.

## 16.4 Représentation des éléments de définition

Le Profile Safran ne modélise pas l'architecture par des instances. Pour les checkers de représentation :

- l'élément de définition lui-même doit être retourné par `getElementsInDiagram()` ;
- le propriétaire du diagramme ne compte pas automatiquement ;
- un Logical System présent uniquement dans un Logical Architecture Diagram ne satisfait pas une règle exigeant un Logical Breakdown Structure ;
- les `IRPClassifierRole` sont utilisés uniquement comme lifelines de séquence.

## 16.5 Operational Flow et cohérence des vues

Un Operational Flow valide relie deux Flow Ports de deux Operational Systems ou Stakeholders distincts. Chaque objet flow doit être représenté dans au moins un Context Diagram ou Environment Diagram. Le même objet peut être présent dans plusieurs vues. Plusieurs objets flows distincts reliant la même paire non ordonnée de ports sont NOK.

## 16.6 Exclusivité des stéréotypes

Un élément peut porter plusieurs stéréotypes non exclusifs. Si un stéréotype appliqué possède `Exclusive = Yes`, un seul stéréotype exclusif peut être appliqué au même élément. Cette règle doit être portée par un checker transversal officiel distinct de `UnclassifiedGenericFlowChecker`.

## 17. Règles de conception des checkers

Chaque checker DOIT :

1. filtrer précisément son périmètre d'applicabilité ;
2. utiliser le containment réel et les identités GUID ;
3. respecter l'orientation des relations ;
4. distinguer l'existence d'un élément de sa représentation dans un diagramme ;
5. retourner les éléments réellement fautifs ;
6. produire un message exploitable ;
7. éviter les faux positifs sur les éléments seulement cibles d'une règle orientée ;
8. définir le comportement sur les éléments hérités ou redéfinis ;
9. documenter les cas à la marge ;
10. être exécuté sur les scénarios OK, NOK et edge du modèle de validation.

### 17.1 Périmètre de représentation

Lorsqu'une règle parle d'un élément « représenté dans un diagramme », le checker DOIT préciser si la contrainte est :

- globale au projet ;
- globale à l'élément ;
- locale à une vue particulière.

Pour les Operational Flows, la règle validée est globale à l'objet flow : chaque flow sémantiquement valide doit apparaître dans au moins un Context Diagram ou Environment Diagram. Le même objet flow peut apparaître dans plusieurs vues. Plusieurs objets flows distincts sur la même paire de ports sont des doublons NOK.

### 17.2 Cas intentionnellement invalides

Un générateur de scénario NOK DEVRAIT :

1. construire d'abord un modèle valide lorsque l'API/Profile l'exige ;
2. appliquer ensuite la violation volontaire ;
3. documenter la violation ;
4. ne pas provoquer une erreur native sans rapport avec le checker.

## 18. Template de package de validation

### 18.1 Package checker

```text
<ID>_<NomClasseChecker>
```

Il contient un package par scénario et une documentation rappelant :

- définition du checker ;
- sévérité ;
- métaclasses configurées ;
- procédure de test ;
- éléments attendus NOK ;
- éléments attendus OK ;
- actions manuelles.

### 18.2 Package scénario

Nom :

```text
<NN>_<OK|NOK|EDGE>_<NOM_EXPLICITE>
```

Il DOIT contenir un commentaire `TEST_EXPECTATION` décrivant :

- résultat attendu ;
- raison ;
- éléments à déclarer ou à ignorer ;
- correction permettant de revenir à un modèle valide.

### 18.3 Résultats autorisés

- **OK** : aucun élément du scénario ne doit être déclaré ;
- **NOK** : le ou les éléments ciblés doivent être déclarés ;
- **MIXTE** : cas OK et NOK volontairement présents dans le même package ;
- **EDGE / MANUEL** : décision métier ou comportement Rhapsody à confirmer.

## 19. Threading, robustesse et logs

- Les objets API Rhapsody NE DOIVENT PAS être transmis entre threads.
- Les appels API doivent rester sur le thread d'exécution compatible avec Rhapsody.
- Une exception API NE DOIT PAS être ignorée silencieusement lorsqu'elle empêche de distinguer « élément absent » de « appel API en échec ».
- Les chemins structurants comprennent au minimum la lecture des owners, des extrémités de flow, des ports, des redéfinitions, des éléments de diagramme, des types de relation et des transitions.
- Les diagnostics structurants DOIVENT être contrôlés et dédupliqués par opération, identité d'élément et type d'exception afin d'éviter de saturer la fenêtre Rhapsody Output.
- Les fallbacks défensifs PEUVENT retourner une valeur vide seulement si ce comportement est documenté et tracé à un niveau de diagnostic approprié.
- Les recherches projet-globales répétées doivent être mises en cache par exécution lorsque le modèle industriel est volumineux.

## 20. Règles de code Java du projet

Chaque fichier Java DOIT commencer par :

```java
// modificationCounter = N
//
// changelog:
// - YYYY-MM-DD - Résumé de la modification.
```

Règles complémentaires :

- noms explicites ;
- pas d'abréviation non standard ;
- Java 17 par défaut ;
- messages d'erreur contextualisés ;
- aucune exception muette ;
- constantes nommées ;
- Javadoc concise sur les classes et méthodes structurantes ;
- modification incrémentale du code existant ;
- patch logique avant réécriture complète.

## 21. Méthode d'analyse obligatoire

### 21.1 Pour un Profile

1. inventorier tous les stéréotypes et new terms ;
2. relever les métaclasses ;
3. extraire `Name`, `Aggregates`, `Sources`, `Targets`, `DrawingToolbar`, `AddNewMenuStructure` ;
4. construire le graphe de containment ;
5. construire le graphe des relations ;
6. construire les matrices de diagrammes et menus ;
7. détecter les références inconnues et noms obsolètes ;
8. comparer Profile, générateur et checkers ;
9. classer chaque problème : Profile, générateur, checker, limitation Rhapsody ou scénario volontaire.

### 21.2 Pour un modèle

1. vérifier le propriétaire réel ;
2. vérifier le new term ;
3. vérifier les types et tags métier ;
4. vérifier les relations et leur orientation ;
5. vérifier les représentations graphiques ;
6. vérifier les éléments hérités/redéfinis ;
7. vérifier les éléments référencés dans les séquences ;
8. comparer le résultat attendu des checkers.

### 21.3 Pour un checker

1. lire la définition XML ;
2. lire la classe wrapper ;
3. lire la méthode de service réelle ;
4. associer tous les scénarios de la spécification courante ;
5. simuler chaque scénario nominal ;
6. simuler chaque scénario edge ;
7. repérer les recherches symétriques appliquées à une règle orientée ;
8. repérer les recherches globales appliquées à une règle locale ;
9. relever les hypothèses API ;
10. classer le résultat : conforme, correction requise, décision métier, test runtime ou blocage Profile.

## 22. Checklist de validation avant livraison

- [ ] Tous les noms de new terms sont exacts.
- [ ] Aucune catégorie Browser n'est utilisée comme propriétaire.
- [ ] Chaque création respecte `Aggregates`.
- [ ] Chaque relation respecte Sources, Targets et stockage.
- [ ] Les packages spécialisés sont créés par Package standard puis `changeTo` lorsque nécessaire.
- [ ] L'objet retourné par `changeTo` est utilisé.
- [ ] Tous les Flow Items sont dans `F - Flow Package`.
- [ ] Les Flow Ports sont typés avant la direction.
- [ ] Les flows sont créés avant les diagrammes.
- [ ] Les séquences sont générées après construction complète de la collaboration.
- [ ] Les formal types sont réappliqués et vérifiés.
- [ ] Les checkers ont été comparés aux scénarios OK, NOK et edge.
- [ ] Les limitations du Profile sont séparées des erreurs de code.
- [ ] Les signatures API proviennent de `java_api.zip` ou d'une source prioritaire.

---

# PARTIE B - EXEMPLES NON NORMATIFS

Les exemples ci-dessous illustrent les règles. Ils ne remplacent jamais l'analyse du Profile réel.

## Exemple 1 - Création sûre d'un package spécialisé

```java
IRPModelElement standardPackage = owner.addNewAggr("Package", "B_Functional_Analysis");
IRPModelElement convertedPackage = standardPackage.changeTo("B - Functional Analysis Package");

// Ne plus utiliser standardPackage après changeTo.
verifyNewTerm(convertedPackage, "B - Functional Analysis Package");
```

## Exemple 2 - Flow Port typé avant direction

```java
IRPSysMLPort flowPort = createNativeSysMLPort(owner, "Pressure");
applyNewTerm(flowPort, "Flow Port");
flowPort.setType(pressureFlowItem);
flowPort.setPortDirection("Out");
```

## Exemple 3 - Scénario NOK de cohérence de type

```text
01_NOK_PORT_TYPES_DIFFER
├── Operational System A
│   └── Flow Port : Type_A / Out
├── Operational System B
│   └── Flow Port : Type_B / In
└── Operational Flow : conveyed Type_A
```

Attendu : déclaration du flow et des ports incohérents. Correction : appliquer un Flow Item identique aux deux ports et au flow.

## Exemple 4 - Parent/enfant dans un diagramme d'architecture

```text
Parent Logical System  [propriétaire du diagramme]
└── Child Logical System [seul nœud ajouté]
```

Les ports du parent sont affichés sur le cadre. Après création des ports et du flow, appeler `completeRelations`.

## Exemple 5 - Diagramme de séquence et formal type

```java
IRPCollaboration collaboration = project.getNewCollaboration();
IRPClassifierRole role = collaboration.addClassifierRole("systemRole", operationalSystem);
IRPMessage mark = createConditionMark(collaboration, role, "ModeSelection");
mark.setFormalType(operatingMode);

IRPSequenceDiagram diagram = collaboration.generateSequence("OperationalSequence", ownerPackage);
IRPCollaboration generated = diagram.getLogicalCollaboration();
IRPMessage generatedMark = findMessageByName(generated.getMessages(), "ModeSelection");
generatedMark.setFormalType(operatingMode);

assertSameElement(generatedMark.getFormalType(), operatingMode);
```

## Exemple 6 - With Reference valide

```text
Referenced Function
└── Flow Port OriginalName : FlowItem_A / Out

Function With Reference
├── References Function -> Referenced Function
└── Flow Port RenamedPort
    ├── redefines -> OriginalName
    ├── type = FlowItem_A
    └── direction = Out
```

## Exemple 7 - Include et extend entre Use Cases uniquement

Cas valide :

```text
Use_Case_A --include--> Use_Case_B
```

Cas NOK :

```text
Use_Case_A --include--> Function_X
Function_X --extend--> Use_Case_B
```

Les deux extrémités de la dépendance doivent être des Use_Cases.

## Exemple 8 - Même flow dans plusieurs diagrammes

```text
F1 : System_A::Port_A <-> System_B::Port_B
Context Diagram 1 : représente F1
Context Diagram 2 : représente le même objet F1
```

Ce cas est OK. En revanche, créer F2 entre les mêmes ports est NOK : conserver un seul flow et utiliser `completeRelations(...)` dans les autres vues.

## Exemple 9 - Commentaire de scénario

```text
TEST_EXPECTATION
Résultat attendu : NOK
Éléments attendus : NOK_PortTypeMismatch_Operational Flow, Port_A, Port_B
Justification : les deux ports n'utilisent pas le même Flow Item.
Correction : typer les deux ports et le flow avec le même Flow Item.
```

---

# PARTIE C - SOURCES ET TRAÇABILITÉ

## Sources locales prioritaires

- `Regles_Analyse_Construction_Profiles_IBM_Rhapsody.docx`
- `java_api.zip` - Javadoc IBM Rhapsody 10.0.2
- `SafranArchitectureProfile.sbsx`
- `SafranStereotypes.sbsx`
- `PredefinedTypes.sbs`
- `SafranCheckerValidationModelGenerator_v20.java`
- `SafranCheckerPlugin` - sources des 40 checkers
- `Specification_implementation_checkers_Safran_v5.xlsx`
- logs de génération et décisions métier consolidées au 24 juillet 2026

## Documentation IBM officielle

- IBM Rhapsody 10.0.2 - Java API :
  https://www.ibm.com/docs/en/engineering-lifecycle-management-suite/design-rhapsody/10.0.2?topic=api-java-version-rhapsody
- IBM Rhapsody API reference :
  https://www.ibm.com/docs/en/engineering-lifecycle-management-suite/design-rhapsody/10.0.2?topic=reference-rhapsody-api
- How to work with the Rhapsody API? :
  https://www.ibm.com/support/pages/how-work-rhapsody-api
- How to add elements to a sequence diagram via the Rhapsody API :
  https://www.ibm.com/support/pages/how-add-elements-sequence-diagram-rhapsody-api

## Sources complémentaires

- Rhapsody Guru : https://www.rhapsody.guru/
- Merlin's Cave : https://www.merlinscave.info/
- Jazz.net / Planet Jazz : https://jazz.net/planet/

Les sources complémentaires servent à comprendre les pratiques et contournements. Elles ne remplacent pas les signatures de `java_api.zip` ni les comportements observés dans Rhapsody 10.0.2.
