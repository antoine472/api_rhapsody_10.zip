# Synthèse des incohérences du profil Rhapsody et des checkers SafranChecker

**Date :** 2026-07-20  
**Profil analysé :** `SafranArchitectureProfile.sbsx`  
**Périmètre :** règles `With Reference`, relations `Redefines`, diagrammes, séquences opérationnelles et checkers associés.

---

## 1. Règles d’interprétation du profil à retenir

### 1.1 Identification d’un new term

Un new term doit toujours être référencé par la valeur de :

```text
Model.Stereotype.Name
```

Le nom interne du stéréotype Rhapsody ne doit pas être utilisé dans les propriétés métier lorsque sa valeur `Model.Stereotype.Name` est différente.

Exemple :

```text
Nom interne : TechnicalComponentWithReference
Nom métier   : Technical Component With Reference
```

La valeur à utiliser dans `Aggregates`, `Sources`, `Targets` et les autres propriétés métier est :

```text
Technical Component With Reference
```

### 1.2 Listes de new terms

Lorsqu’une propriété contient plusieurs types, les valeurs sont séparées par une virgule :

```text
Logical System, Operational System
```

Chaque valeur doit correspondre exactement à un `Model.Stereotype.Name`.

### 1.3 Containment

Les éléments pouvant être contenus par un new term sont définis par :

```text
Model.Stereotype.Aggregates
```

Un élément profilé doit être créé directement avec son new term autorisé, et non comme une métaclasse UML native ensuite stéréotypée.

### 1.4 Flows et dependencies

Pour un flow ou une dependency :

```text
Model.Stereotype.Sources
Model.Stereotype.Targets
```

définissent les types autorisés aux extrémités.

La relation est généralement stockée sous l’élément source. Son new term doit donc aussi être présent dans le `Model.Stereotype.Aggregates` de la source, ou d’un propriétaire métier explicitement prévu à cet effet.

### 1.5 Generalizations

Les relations suivantes sont des generalizations :

```text
Redefines Function
Redefines System
Redefines Component
References Function
References Logical System
References Technical Component
```

Rhapsody ne permet pas de restreindre techniquement leurs sources et cibles avec `Sources` et `Targets`.

Ces propriétés restent utiles comme documentation du profil, mais les règles métier doivent être contrôlées par les checkers.

### 1.6 DrawingToolbar

La règle précédente sur `DrawingToolbar` doit être corrigée.

`Model.Stereotype.DrawingToolbar` définit les outils disponibles pour **créer de nouveaux éléments depuis la toolbar du diagramme**.

L’absence d’un type dans `DrawingToolbar` ne signifie pas qu’un élément existant ne peut pas être représenté dans le diagramme.

Règle corrigée :

> Un élément existant peut être ajouté à un diagramme même s’il n’est pas présent dans `Model.Stereotype.DrawingToolbar`.  
> Lorsqu’un checker exige explicitement la représentation d’un élément dans un diagramme, cette exigence est prioritaire.  
> `DrawingToolbar` doit uniquement être utilisé pour vérifier les créations proposées depuis le diagramme.

---

# 2. Incohérences identifiées dans `SafranArchitectureProfile.sbsx`

## P-01 — Cible incorrecte pour `References Logical System`

### Valeur actuelle

```text
New term : References Logical System
Sources  : Logical System With Reference
Targets  : Logical System, Operational System
```

### Règle métier validée

Un `Logical System With Reference` peut référencer uniquement un `Logical System`.

Un `Logical System` peut en revanche **redéfinir** un `Logical System` ou un `Operational System`. Cette règle concerne `Redefines System`, pas `References Logical System`.

### Correction à appliquer

```text
Model.Stereotype.Targets = Logical System
```

---

## P-02 — Nom obsolète dans `References Technical Component.Sources`

### Valeur actuelle

```text
New term : References Technical Component
Sources  : Technical Component Reference
Targets  : Technical Component
```

Le new term `Technical Component Reference` n’existe pas avec ce nom métier dans le profil actuel.

Le new term disponible est :

```text
Technical Component With Reference
```

### Correction à appliquer

```text
Model.Stereotype.Sources = Technical Component With Reference
```

La cible reste :

```text
Model.Stereotype.Targets = Technical Component
```

---

## P-03 — Nom obsolète dans `System Realization.Sources`

### Valeur actuelle

```text
New term : System Realization
Sources  : Technical Component, Technical Component Reference
Targets  : Logical System, Logical System With Reference
```

`Technical Component Reference` est l’ancien nom de `Technical Component With Reference`.

### Correction minimale à appliquer

```text
Model.Stereotype.Sources =
Technical Component, Technical Component With Reference
```

### Remarque

Le profil autorise actuellement comme cibles :

```text
Logical System, Logical System With Reference
```

La décision métier confirmée porte au minimum sur :

```text
Technical Component ──System Realization──> Logical System
```

La conservation de `Logical System With Reference` comme cible n’a pas été invalidée dans les échanges. Elle peut donc être conservée tant qu’une règle métier contraire n’est pas définie.

---

## P-04 — Ports absents des `Aggregates` des éléments `With Reference`

La règle métier autorise la redéfinition locale d’un port uniquement pour changer son nom.

Une redéfinition de port crée néanmoins un port local sous l’élément `With Reference`. Le type de port doit donc être autorisé dans `Aggregates`.

### Valeurs actuelles

#### Function With Reference

```text
Aggregates =
References Function, Comment, Dependency, HyperLink, Tag
```

#### Logical System With Reference

```text
Aggregates =
References Logical System, Comment, Dependency, HyperLink, Tag
```

#### Technical Component With Reference

```text
Aggregates =
References Technical Component, System Realization,
Comment, Dependency, HyperLink, Tag
```

### Corrections à appliquer

#### Function With Reference

Ajouter :

```text
Flow Port
```

Valeur proposée :

```text
References Function, Flow Port, Comment, Dependency, HyperLink, Tag
```

#### Logical System With Reference

Ajouter :

```text
Flow Port
```

Valeur proposée :

```text
References Logical System, Flow Port, Comment, Dependency, HyperLink, Tag
```

#### Technical Component With Reference

Ajouter :

```text
Technical Port
```

Valeur proposée :

```text
References Technical Component, Technical Port, System Realization,
Comment, Dependency, HyperLink, Tag
```

### Interdiction à conserver

Les éléments `With Reference` ne doivent pas permettre l’ajout libre de nouveaux ports métier.

Le profil doit permettre techniquement la création d’un port local pour la redéfinition, mais le checker doit vérifier que chaque port local redéfinit bien un port du modèle référencé.

---

## P-05 — `Operational System With Reference` absent du profil

La règle métier validée indique qu’un `Operational System With Reference` doit référencer uniquement un `Operational System`.

Le profil analysé ne contient actuellement ni :

```text
Operational System With Reference
```

ni :

```text
References Operational System
```

### Éléments à ajouter si ce concept est dans le périmètre du profil

#### New term `Operational System With Reference`

Métaclasse :

```text
Class
```

Nom métier :

```text
Model.Stereotype.Name = Operational System With Reference
```

Aggregates minimaux proposés :

```text
References Operational System, Flow Port,
Comment, Dependency, HyperLink, Tag
```

Le new term doit également être ajouté aux `Aggregates` du ou des propriétaires depuis lesquels l’utilisateur doit pouvoir le créer, ainsi qu’à la whitelist du profil.

#### New term `References Operational System`

Métaclasse :

```text
Generalization
```

Propriétés documentaires :

```text
Model.Stereotype.Name    = References Operational System
Model.Stereotype.Sources = Operational System With Reference
Model.Stereotype.Targets = Operational System
```

---

## P-06 — Source documentaire de `Redefines System`

### Valeur actuelle

```text
New term : Redefines System
Sources  : Logical System, Functional System
Targets  : Operational System, Logical System
```

### Règle métier validée

Un `Logical System` peut redéfinir :

```text
Operational System
Logical System
```

### Correction documentaire recommandée

```text
Model.Stereotype.Sources = Logical System
Model.Stereotype.Targets = Operational System, Logical System
```

Cette correction améliore la cohérence du profil, même si Rhapsody ne fait pas respecter `Sources` et `Targets` sur une generalization.

---

## P-07 — Use Case Diagram : aucune correction de DrawingToolbar

### Valeurs actuelles pertinentes

```text
Lifecycle Phase.Aggregates contient : Use_Case Diagram
```

```text
Use_Case Diagram.DrawingToolbar =
Directed Association, Extend, Include, Satisfies, System Border
```

### Conclusion

Le profil est cohérent sur ce point :

- un `Use_Case Diagram` est contenu dans une `Lifecycle Phase` ;
- le Use Case Diagram ne permet pas de créer un `Use_Case` depuis sa toolbar ;
- un `Use_Case` existant peut néanmoins être ajouté au diagramme par représentation ou drag-and-drop.

Il ne faut pas ajouter artificiellement `Use_Case` dans `DrawingToolbar`.

---

## P-08 — Technical Component et Logical System : profil cohérent

`Technical Component.Aggregates` contient notamment :

```text
Technical Component
Technical Port
Flow Port
Technical Flow
Technical Connection
System Realization
```

Il ne contient pas :

```text
Logical System
```

Cette structure est correcte.

La relation métier attendue est :

```text
Technical Component
    └── System Realization ──> Logical System
```

Il ne faut pas ajouter `Logical System` dans `Technical Component.Aggregates`.

---

# 3. Règle générale des éléments `With Reference`

Les anciens termes « Reuse » doivent être supprimés des exigences, tests, noms de scénarios et messages.

Correspondances :

| Ancien terme | Nouveau terme |
|---|---|
| Function Reuse | Function With Reference |
| Logical System Reuse | Logical System With Reference |
| Technical Component Reuse | Technical Component With Reference |
| Operational System Reuse | Operational System With Reference |

## 3.1 Règle commune

Un élément `With Reference` :

1. référence exactement un élément de définition du même type ;
2. ne peut pas supprimer un port de l’élément référencé ;
3. ne peut pas ajouter un nouveau port métier ;
4. peut redéfinir localement un port existant pour modifier son nom ;
5. doit tracer chaque port local vers le port original avec la relation de redéfinition native du port ;
6. peut avoir des tags et des stéréotypes différents ;
7. n’est pas soumis à une comparaison de description, tags ou stéréotypes supplémentaires.

## 3.2 Comparaison des Flow Ports

Pour un Flow Port redéfini :

- le nom peut être identique ou différent ;
- la direction doit être identique ;
- le type `Flow Item` doit être identique ;
- les tags peuvent différer ;
- les stéréotypes supplémentaires peuvent différer.

L’identité du port doit être déterminée par sa relation de redéfinition, pas par son nom.

## 3.3 Comparaison des Technical Ports

Pour un Technical Port redéfini :

- le nom est la seule propriété métier autorisée à changer ;
- le `port contract` doit être identique ;
- les tags peuvent différer ;
- les stéréotypes supplémentaires peuvent différer.

Le contrôle du nom consiste à accepter le renommage, et non à imposer l’égalité des noms.

L’identité du port doit être déterminée par sa relation de redéfinition, pas par son nom.

---

# 4. Définitions de checkers à corriger

## 207924 — Function With Reference compatibility

### Problème actuel

Le checker est encore défini comme un contrôle de `Redefines Function` et utilise l’ancien terme « Function Reuse ».

### Nouvelle classe recommandée

```text
FunctionWithReferenceCompatibilityChecker
```

### Métaclasse configurée

```text
Function With Reference
```

### Nouvelle définition anglaise proposée

> The checker shall detect and report any Function With Reference that does not reference exactly one Function, does not preserve all Flow Ports of the referenced Function, introduces a Flow Port that does not redefine a Flow Port of the referenced Function, or whose redefined Flow Ports have a different direction or Flow Item type from their original ports.

### Règles de contrôle

- exactement une relation `References Function` ;
- cible obligatoirement `Function` ;
- aucun Flow Port supprimé ;
- aucun Flow Port local sans redéfinition d’un port original ;
- direction identique ;
- type `Flow Item` identique ;
- renommage autorisé ;
- tags et stéréotypes ignorés.

---

## 207925 — Logical System With Reference compatibility

### Problème actuel

Le checker est encore défini comme un contrôle de `Redefines System`, utilise l’ancien terme « Logical System Reuse » et mentionne une comparaison des Functions.

### Nouvelle classe recommandée

```text
LogicalSystemWithReferenceCompatibilityChecker
```

### Métaclasse configurée

```text
Logical System With Reference
```

### Nouvelle définition anglaise proposée

> The checker shall detect and report any Logical System With Reference that does not reference exactly one Logical System, does not preserve all Flow Ports of the referenced Logical System, introduces a Flow Port that does not redefine a Flow Port of the referenced Logical System, or whose redefined Flow Ports have a different direction or Flow Item type from their original ports.

### Règles de contrôle

- exactement une relation `References Logical System` ;
- cible obligatoirement `Logical System` ;
- une cible `Operational System` est NOK ;
- aucun Flow Port supprimé ;
- aucun Flow Port local sans redéfinition d’un port original ;
- direction identique ;
- type `Flow Item` identique ;
- renommage autorisé ;
- tags et stéréotypes ignorés ;
- supprimer de cette exigence l’ancienne comparaison des Functions, sauf création ultérieure d’une règle métier distincte.

---

## 207927 — Technical Component With Reference compatibility

### Problème actuel

Le checker contrôle actuellement `Redefines Component`, utilise l’ancien terme « Technical Component Reuse » et demande une comparaison de Logical Systems contenus.

Cette définition est incorrecte :

- un `Redefines Component` peut modifier ou ajouter des ports ;
- un `Technical Component` ne contient pas de `Logical System` ;
- la relation avec un Logical System est `System Realization`.

### Nouvelle classe recommandée

```text
TechnicalComponentWithReferenceCompatibilityChecker
```

### Métaclasse configurée

```text
Technical Component With Reference
```

### Nouvelle définition anglaise proposée

> The checker shall detect and report any Technical Component With Reference that does not reference exactly one Technical Component, does not preserve all Technical Ports of the referenced Technical Component, introduces a Technical Port that does not redefine a Technical Port of the referenced Technical Component, or whose redefined Technical Ports use a different port contract from their original ports.

### Règles de contrôle

- exactement une relation `References Technical Component` ;
- cible obligatoirement `Technical Component` ;
- aucun Technical Port supprimé ;
- aucun Technical Port local sans redéfinition d’un port original ;
- le nom peut changer ;
- le `port contract` doit être identique ;
- tags et stéréotypes ignorés ;
- supprimer toute comparaison de Logical Systems contenus ;
- ne pas appliquer cette règle aux relations `Redefines Component`.

---

## 206485 — Use Case represented in a Use Case Diagram

### Définition corrigée ou précisée

> The checker shall detect and report any Use_Case that is not represented in at least one Use_Case Diagram contained in a Lifecycle Phase.

### Règles de contrôle

- le Use Case Diagram doit être contenu dans une `Lifecycle Phase` ;
- le `Use_Case` doit être réellement représenté dans le diagramme ;
- l’ownership du diagramme par le Use Case n’est pas valide ;
- l’absence de `Use_Case` dans `DrawingToolbar` ne doit pas empêcher le contrôle.

### Scénario OK

```text
Lifecycle Phase
├── Use_Case
└── Use_Case Diagram
    └── représentation du Use_Case existant
```

---

## 206483 — Use Case associated with an external actor

### Définition anglaise proposée

> The checker shall detect and report any Use_Case that is not connected to at least one Stakeholder or external Operational System by either a Directed Association or a standard UML Association.

### Relations acceptées

```text
Directed Association
Association UML non dirigée
```

Toute autre `IRPRelation` ne doit pas être acceptée automatiquement.

---

## 206489 — Action Block, Function ownership and mode availability

### Problème actuel

Le checker analyse actuellement directement une dependency `Activated Function`.

Dans un Operational Sequence Diagram :

- une Function est représentée par un `ActionBlock` ;
- l’ActionBlock référence la Function ;
- un Operating Mode est représenté par un `ConditionMark` ;
- le ConditionMark référence l’Operating Mode ;
- `Activated Function` est une dependency du modèle entre l’Operating Mode et la Function ;
- `Activated Function` n’est pas représentée dans le diagramme de séquence.

### Métaclasse configurée recommandée

```text
Operational Sequence Diagram
```

ou, si le registre externe l’autorise de manière fiable :

```text
ActionBlock
```

### Nouvelle définition anglaise proposée

> The checker shall detect and report any Action Block in an Operational Sequence Diagram that references a Function not owned by the Operational System represented by its lifeline, or, when a preceding Condition Mark on the same lifeline defines a current Operating Mode, whose Function is not available in that mode through an Activated Function dependency.

### Algorithme métier

1. parcourir les Action Blocks du diagramme ;
2. récupérer la Function référencée par l’ActionBlock ;
3. si l’ActionBlock ne référence aucune Function, retourner OK pour cet ActionBlock ;
4. identifier l’Operational System représenté par la lifeline ;
5. vérifier que la Function appartient à cet Operational System ;
6. rechercher le dernier ConditionMark précédant l’ActionBlock sur la même lifeline ;
7. s’il n’existe aucun ConditionMark précédent, retourner OK pour le contrôle de mode ;
8. récupérer l’Operating Mode référencé par le ConditionMark ;
9. vérifier l’existence de :

```text
Operating Mode ──Activated Function──> Function
```

10. si la dependency est absente, retourner NOK.

---

## 206496 — Condition Mark assigned to an Operating Mode of the SOI

### Nouvelle définition anglaise proposée

> The checker shall detect and report any Condition Mark in an Operational Sequence Diagram that does not reference an Operating Mode owned by the Operational System designated as <<SystemOfInterest>>.

### Règles de contrôle

- le ConditionMark référence directement son Operating Mode ;
- la référence doit être lue via le `formalType` du message de séquence ;
- le formal type doit être un `Operating Mode` ;
- l’Operating Mode doit appartenir au système portant `SystemOfInterest` ;
- ne pas rechercher une dependency artificielle entre ConditionMark et Operating Mode.

Signature API confirmée dans la documentation Java Rhapsody :

```java
IRPModelElement IRPMessage.getFormalType()
void IRPMessage.setFormalType(IRPModelElement formalType)
```

---

## 206491 — Lifecycle Phase connectivity

### Problème actuel

La définition exige une connexion à une autre Lifecycle Phase.

Le cas suivant est pourtant valide :

```text
Start Phase ──> Lifecycle Phase ──> End Phase
```

### Nouvelle définition anglaise proposée

> The checker shall detect and report any Lifecycle Phase that is not connected by a Lifecycle Phase Transition to at least one Start Phase, Lifecycle Phase, or End Phase.

### Règles de contrôle

Une Lifecycle Phase est OK si elle participe à une `Lifecycle Phase Transition` valide avec :

- `Start Phase` ;
- une autre `Lifecycle Phase` ;
- `End Phase`.

Une Lifecycle contenant une seule phase entre Start et End est OK.

---

## 206495 — Presence of DataFlow exchanges

### Problème actuel

La définition DNG est vide ou insuffisamment précise.

### Nouvelle définition anglaise proposée

> The checker shall detect and report any Operational Sequence Diagram containing at least two represented Operational Systems when no DataFlow message is exchanged between their lifelines.

### Responsabilité du checker

- vérifier la présence d’au moins un DataFlow entre des lifelines d’Operational Systems ;
- ne pas vérifier le Flow Item ;
- ne pas vérifier l’Operational Flow correspondant ;
- déléguer ces contrôles de cohérence à `210753`.

---

## 210753 — DataFlow, Flow Item and Operational Flow coherence

### Nouvelle définition anglaise proposée

> The checker shall detect and report any DataFlow message in an Operational Sequence Diagram that has no dependency to a Flow Item, or whose associated Flow Item is not conveyed by an Operational Flow connecting the Operational Systems represented by the source and target lifelines.

### Règles de contrôle

Pour chaque DataFlow :

1. identifier les Operational Systems des lifelines source et cible ;
2. rechercher une dependency depuis le message vers un `Flow Item` ;
3. si aucune dependency n’existe, retourner NOK ;
4. rechercher un `Operational Flow` reliant les deux systèmes ;
5. vérifier que ce flow contient le même `Flow Item` dans ses éléments convoyés ;
6. si aucun flow cohérent n’est trouvé, retourner NOK ;
7. ne pas contrôler ici la direction du message par rapport au flow.

---

## 206504 — Final state of an Operating Mode Chart

### Définition anglaise proposée

> The checker shall detect and report any Operating Mode Chart that does not contain a TerminationState.

### Règle de contrôle

Utiliser l’élément du profil ou la métaclasse native correspondant à :

```text
TerminationState
```

Ne pas se limiter à une recherche textuelle contenant `final` ou `termination`.

---

## 206505 — Initial state or default transition

### Définition conservée et précisée

> The checker shall detect and report any Operating Mode Chart that has neither an initial state nor a DefaultTransition.

### Règle de contrôle

Utiliser en priorité les éléments définis ou proposés par le profil :

```text
DefaultTransition
Initial state / initial connector Rhapsody
```

---

# 5. Checkers dont la définition reste valide mais dont l’implémentation doit respecter les diagrammes

## 206498 — Operational connectivity in Context/Environment Diagram

La définition reste valide.

Le checker doit analyser les Operational Systems et Stakeholders réellement représentés dans le diagramme, même s’ils ne sont pas créables depuis `DrawingToolbar`.

Les seuls Flow Ports représentés ne remplacent pas la représentation attendue des systèmes lorsque le checker porte explicitement sur ces systèmes.

## 206501 — Operational System representation

La définition reste valide.

Un Operational System existant ajouté à un Context Diagram ou Environment Diagram compte comme représenté, même s’il n’apparaît pas dans la toolbar de création.

## 207822 — SystemOfInterest in sequence diagram

La définition reste valide.

Le stéréotype `SystemOfInterest` est appliqué à l’Operational System, dont la métaclasse est `Class`, et non au `ClassifierRole` de la lifeline.

---

# 6. Modifications à appliquer dans la configuration et le code

## 6.1 `safran-checkers.xml`

Mettre à jour au minimum :

| ID | Java class proposée | Métaclasse configurée |
|---:|---|---|
| 207924 | `FunctionWithReferenceCompatibilityChecker` | `Function With Reference` |
| 207925 | `LogicalSystemWithReferenceCompatibilityChecker` | `Logical System With Reference` |
| 207927 | `TechnicalComponentWithReferenceCompatibilityChecker` | `Technical Component With Reference` |
| 206489 | `OperationalSequenceActionBlockChecker` | `Operational Sequence Diagram` |
| 206496 | `OperationalSequenceConditionMarkChecker` | `Operational Sequence Diagram` |
| 206495 | `OperationalSequenceFlowPresenceChecker` | `Operational Sequence Diagram` |
| 210753 | `OperationalSequenceFlowTypingChecker` | `Operational Sequence Diagram` |

Les noms de classes restent une proposition. Les identifiants DNG doivent être conservés.

## 6.2 `CheckerRuleService`

Remplacer les contrôles actuels de :

```text
Redefines Function
Redefines System
Redefines Component
```

pour les IDs `207924`, `207925`, `207927` par des contrôles des éléments `With Reference` et de leurs relations `References ...`.

Conserver les règles `Redefines` hors de ces trois checkers.

## 6.3 Générateur de modèle de validation

Créer des scénarios couvrant au minimum :

### Pour chaque `With Reference`

- NOK : aucune relation `References ...` ;
- NOK : plusieurs relations `References ...` ;
- NOK : mauvaise cible ;
- NOK : port local ajouté sans redéfinition ;
- NOK : port original non préservé ;
- NOK : propriété interdite modifiée ;
- OK : aucun port local, ports hérités disponibles ;
- OK : port redéfini avec renommage uniquement ;
- OK : tags ou stéréotypes locaux différents.

### Pour `206489`

- OK : ActionBlock sans Function ;
- NOK : Function appartenant au mauvais système ;
- OK : Function correcte sans ConditionMark précédent ;
- NOK : mode courant défini, dependency `Activated Function` absente ;
- OK : mode courant défini et Function activée.

### Pour `210753`

- NOK : DataFlow sans dependency vers Flow Item ;
- NOK : dependency présente mais Flow Item absent des Operational Flows entre les systèmes ;
- OK : dependency présente et même Flow Item convoyé par un Operational Flow cohérent.

---

# 7. Ordre recommandé des modifications

1. Corriger `regles_rhapsody.md` sur la signification de `DrawingToolbar`.
2. Corriger les noms obsolètes du profil :
   - `Technical Component Reference`;
   - cibles de `References Logical System`.
3. Ajouter les ports dans les `Aggregates` des éléments `With Reference`.
4. Ajouter `Operational System With Reference` et `References Operational System` si ce concept doit être disponible.
5. Corriger la documentation de `Redefines System`.
6. Modifier les exigences DNG des checkers.
7. Mettre à jour `safran-checkers.xml`.
8. Mettre à jour les classes de checkers.
9. Mettre à jour le générateur de modèles de validation.
10. Rejouer les scénarios NOK, OK et limites avant intégration.

---

# 8. Résumé des corrections prioritaires

| Priorité | Élément | Correction |
|---|---|---|
| Critique | `References Logical System` | Retirer `Operational System` des Targets |
| Critique | `References Technical Component` | Remplacer `Technical Component Reference` par `Technical Component With Reference` |
| Critique | `System Realization` | Remplacer l’ancien nom `Technical Component Reference` |
| Critique | Éléments `With Reference` | Ajouter les types de ports nécessaires dans `Aggregates` |
| Critique | 207924/207925/207927 | Remplacer les contrôles `Redefines`/`Reuse` par des contrôles `With Reference` |
| Haute | 206489 | Contrôler ActionBlock, Function, mode courant et Activated Function |
| Haute | 210753 | Contrôler dependency message → Flow Item puis Operational Flow cohérent |
| Haute | 206496 | Utiliser `ConditionMark.getFormalType()` via `IRPMessage` |
| Haute | DrawingToolbar | Ne plus l’utiliser comme whitelist de représentation |
| Moyenne | `Redefines System` | Corriger la source documentaire |
| Moyenne | Operational System With Reference | Ajouter les new terms manquants si le concept est dans le périmètre |
