# Conception et implémentation des checkers SafranChecker

**Version :** 4.0  
**Date :** 2026-07-20  
**Rhapsody :** 10.0.2  
**Java :** 17

## 1. Objet

Ce document décrit l’architecture, les règles de lecture du profil Rhapsody, les choix d’implémentation et la stratégie de validation des 40 External Checkers du plugin SafranChecker. Il constitue la référence de conception pour `CheckerRuleService`, les classes du package `checkers`, `safran-checkers.xml` et le générateur de modèles de validation.

## 2. Architecture commune

- `SafranCheckerPlugin` charge `safran-checkers.xml` et instancie les classes par réflexion.
- Chaque classe du package `checkers` est un adaptateur minimal : elle délègue à une méthode de `CheckerRuleService`.
- `CheckerRuleService` contient les algorithmes métier et retourne un `CheckOutcome`.
- `RhapsodyModelQueries` centralise les parcours de modèle, diagrammes, flows, messages et relations.
- `ProfileNewTerms` centralise les noms exacts issus de `Model.Stereotype.Name`.
- Le générateur v8 crée des scénarios NOK/OK sous le Project ou Package sélectionné sans sauvegarde automatique.

## 3. Règles de lecture du profil

1. Un new term est identifié par `Model.Stereotype.Name`.
2. `Model.Stereotype.Aggregates` définit les enfants créables sous un élément.
3. `Sources` et `Targets` définissent les extrémités des flows/dependencies.
4. Les restrictions Sources/Targets des generalizations sont contrôlées par les checkers, car Rhapsody ne les impose pas.
5. `DrawingToolbar` définit les outils de création depuis le diagramme, pas les éléments existants pouvant y être représentés.
6. Les valeurs exactes sont `In`, `Out`, `InOut` pour les ports et `toEnd2`, `toEnd1`, `bidirectional` pour les flows.

## 4. Choix transverses

- Comparaison par identité de modèle/GUID, jamais uniquement par nom, sauf checker explicitement nominal.
- Absence d’exception silencieuse dans les règles métier ; les helpers tolèrent uniquement les variantes API documentées et retournent des collections vides lorsque la requête n’est pas applicable.
- Les failed elements incluent l’élément principal et les objets utiles au diagnostic.
- Les parcours globaux sont centralisés pour pouvoir être remplacés ultérieurement par un index si les performances l’exigent.
- `getCompleteness()` reste `false` pour tous les checkers : le contrôle est incrémental et dépend du contexte de modèle chargé.

## 5. Traçabilité synthétique

| ID | Classe Java | Sévérité | Métaclasses | Méthode service |
|---:|---|---|---|---|
| 206469 | `OperationalFlowItemTypeCoherenceChecker` | Error | Operational Flow | `checkOperationalFlowItemTypeCoherence` |
| 206470 | `OperationalFlowPortStereotypeChecker` | Error | Operational Flow | `checkOperationalFlowPortStereotype` |
| 353035 | `OperationalFlowPortDirectionChecker` | Error | Operational Flow | `checkOperationalFlowPortDirection` |
| 206471 | `FunctionalFlowPortDirectionChecker` | Error | Functional Flow | `checkFunctionalFlowPortDirection` |
| 206472 | `FunctionalFlowItemTypeCoherenceChecker` | Error | Functional Flow | `checkFunctionalFlowItemTypeCoherence` |
| 207668 | `FunctionalFlowPortStereotypeChecker` | Error | Functional Flow | `checkFunctionalFlowPortStereotype` |
| 206468 | `LogicalFlowPortDirectionChecker` | Error | Logical Flow | `checkLogicalFlowPortDirection` |
| 207669 | `LogicalFlowItemTypeCoherenceChecker` | Error | Logical Flow | `checkLogicalFlowItemTypeCoherence` |
| 206499 | `LogicalFlowPortStereotypeChecker` | Error | Logical Flow | `checkLogicalFlowPortStereotype` |
| 208311 | `UnclassifiedGenericFlowChecker` | Warning | Generic Flow | `checkUnclassifiedGenericFlow` |
| 206498 | `OperationalContextConnectivityChecker` | Error | Operational System, Stakeholder | `checkOperationalContextConnectivity` |
| 207822 | `OperationalSequenceSystemOfInterestChecker` | Error | Operational Sequence Diagram | `checkOperationalSequenceSystemOfInterest` |
| 210753 | `OperationalSequenceFlowTypingChecker` | Error | Operational Sequence Diagram | `checkOperationalSequenceFlowTyping` |
| 210761 | `UnconnectedOperationalFlowPortChecker` | Warning | Flow Port | `checkUnconnectedOperationalFlowPort` |
| 210762 | `UnconnectedFunctionalFlowPortChecker` | Warning | Flow Port | `checkUnconnectedFunctionalFlowPort` |
| 210763 | `UnconnectedLogicalFlowPortChecker` | Warning | Flow Port | `checkUnconnectedLogicalFlowPort` |
| 206481 | `UnusedFlowItemChecker` | Warning | Flow Item | `checkUnusedFlowItem` |
| 206486 | `UnnestedFunctionChecker` | Warning | Function | `checkUnnestedFunction` |
| 207924 | `FunctionWithReferenceCompatibilityChecker` | Error | Function With Reference | `checkFunctionWithReferenceCompatibility` |
| 206479 | `LogicalSystemBreakdownRepresentationChecker` | Warning | Logical System | `checkLogicalSystemBreakdownRepresentation` |
| 206487 | `UnnestedLogicalSystemChecker` | Warning | Logical System | `checkUnnestedLogicalSystem` |
| 207925 | `LogicalSystemWithReferenceCompatibilityChecker` | Error | Logical System With Reference | `checkLogicalSystemWithReferenceCompatibility` |
| 206480 | `TechnicalComponentBreakdownRepresentationChecker` | Warning | Technical Component | `checkTechnicalComponentBreakdownRepresentation` |
| 206488 | `UnnestedTechnicalComponentChecker` | Warning | Technical Component | `checkUnnestedTechnicalComponent` |
| 207927 | `TechnicalComponentWithReferenceCompatibilityChecker` | Error | Technical Component With Reference | `checkTechnicalComponentWithReferenceCompatibility` |
| 206485 | `UseCaseDiagramRepresentationChecker` | Warning | Use_Case | `checkUseCaseDiagramRepresentation` |
| 206493 | `UseCaseIncludeExtendTargetChecker` | Warning | Use_Case | `checkUseCaseIncludeExtendTarget` |
| 206482 | `UseCaseSatisfactionChecker` | Warning | Use_Case | `checkUseCaseSatisfaction` |
| 206483 | `UseCaseExternalActorAssociationChecker` | Warning | Use_Case | `checkUseCaseExternalActorAssociation` |
| 206495 | `OperationalSequenceFlowPresenceChecker` | Warning | Operational Sequence Diagram | `checkOperationalSequenceFlowPresence` |
| 206496 | `OperationalSequenceConditionMarkChecker` | Warning | Operational Sequence Diagram | `checkOperationalSequenceConditionMark` |
| 206489 | `OperationalSequenceActionBlockChecker` | Warning | Operational Sequence Diagram | `checkOperationalSequenceActionBlocks` |
| 206490 | `LifecyclePhaseRepresentationChecker` | Warning | Lifecycle Phase | `checkLifecyclePhaseRepresentation` |
| 206491 | `LifecyclePhaseConnectivityChecker` | Warning | Lifecycle Phase | `checkLifecyclePhaseConnectivity` |
| 206503 | `OperatingModeRepresentationChecker` | Warning | Operating Mode | `checkOperatingModeRepresentation` |
| 206492 | `OperatingModeTransitionChecker` | Error | Operating Mode | `checkOperatingModeTransitions` |
| 206505 | `OperatingModeChartInitialStateChecker` | Warning | Operating Mode Chart | `checkOperatingModeChartInitialState` |
| 206504 | `OperatingModeChartFinalStateChecker` | Warning | Operating Mode Chart | `checkOperatingModeChartFinalState` |
| 206501 | `OperationalSystemDiagramRepresentationChecker` | Warning | Operational System | `checkOperationalSystemDiagramRepresentation` |
| 206484 | `ConnectedFlowPortNameChecker` | Info | Operational Flow, Functional Flow, Logical Flow, Technical Flow | `checkConnectedFlowPortNames` |

## 6. Conception détaillée par checker

### 206469 - OperationalFlowItemTypeCoherenceChecker

**Exigence.** The checker shall detect and report any Operational Flow whose connected Flow Ports or conveyed Flow Item use inconsistent Flow Item types.

**Applicabilité.** Operational Flow - Error.

**Responsabilité.** Operational Flow

**Algorithme.** Resolve end1/end2 Flow Ports and all conveyed elements. Compare the two port types and every conveyed Flow Item by model identity. Missing types are accepted only when both compared values are absent.

**API Rhapsody utilisée.** IRPFlow.getEnd1/getEnd2/getConveyed; IRPSysMLPort.getType.

**Éléments retournés en échec.** The flow, incompatible ports and conveyed items are returned as failed elements.

**Dépendances au profil / décisions.** Operational Flow; Flow Port; Flow Item.

**Scénarios de validation.** mismatched port types; conveyed item mismatch; coherent flow.

**Implémentation.** Adaptateur `OperationalFlowItemTypeCoherenceChecker` → `CheckerRuleService.checkOperationalFlowItemTypeCoherence()`.

### 206470 - OperationalFlowPortStereotypeChecker

**Exigence.** The checker shall detect and report any Operational Flow connected to a port that is not stereotyped as Flow Port.

**Applicabilité.** Operational Flow - Error.

**Responsabilité.** Operational Flow

**Algorithme.** Resolve both flow endpoints. Each endpoint must be an IRPSysMLPort carrying the exact new term Flow Port. A native SysMLPort is NOK.

**API Rhapsody utilisée.** IRPFlow endpoints; IRPModelElement.getUserDefinedMetaClass/getNewTermStereotype.

**Éléments retournés en échec.** Flow and invalid endpoints.

**Dépendances au profil / décisions.** Operational Flow Sources/Targets = Flow Port.

**Scénarios de validation.** one plain SysMLPort; two Flow Ports.

**Implémentation.** Adaptateur `OperationalFlowPortStereotypeChecker` → `CheckerRuleService.checkOperationalFlowPortStereotype()`.

### 353035 - OperationalFlowPortDirectionChecker

**Exigence.** The checker shall detect and report any Operational Flow whose connected Flow Port directions are inconsistent with the flow direction.

**Applicabilité.** Operational Flow - Error.

**Responsabilité.** Operational Flow

**Algorithme.** Use exact Rhapsody values. toEnd2 requires end1=Out/end2=In; toEnd1 requires end1=In/end2=Out; bidirectional requires InOut/InOut. Unknown direction is an explicit technical NOK.

**API Rhapsody utilisée.** IRPFlow.getDirection; IRPSysMLPort.getPortDirection.

**Éléments retournés en échec.** Flow and both ports.

**Dépendances au profil / décisions.** Exact values confirmed: toEnd2, toEnd1, bidirectional; In, Out, InOut.

**Scénarios de validation.** all valid directions; mismatches; unsupported value.

**Implémentation.** Adaptateur `OperationalFlowPortDirectionChecker` → `CheckerRuleService.checkOperationalFlowPortDirection()`.

### 206471 - FunctionalFlowPortDirectionChecker

**Exigence.** The checker shall detect and report any Functional Flow whose connected Flow Port directions are inconsistent with the flow direction.

**Applicabilité.** Functional Flow - Error.

**Responsabilité.** Functional Flow

**Algorithme.** Same exact direction matrix as 353035, restricted to Functional Flow.

**API Rhapsody utilisée.** IRPFlow.getDirection; IRPSysMLPort.getPortDirection.

**Éléments retournés en échec.** Flow and both ports.

**Dépendances au profil / décisions.** Functional Flow Sources/Targets = Flow Port.

**Scénarios de validation.** forward, reverse, bidirectional and mismatches.

**Implémentation.** Adaptateur `FunctionalFlowPortDirectionChecker` → `CheckerRuleService.checkFunctionalFlowPortDirection()`.

### 206472 - FunctionalFlowItemTypeCoherenceChecker

**Exigence.** The checker shall detect and report any Functional Flow whose connected Flow Ports or conveyed Flow Item use inconsistent Flow Item types.

**Applicabilité.** Functional Flow - Error.

**Responsabilité.** Functional Flow

**Algorithme.** Same type-coherence algorithm as 206469, restricted to Functional Flow.

**API Rhapsody utilisée.** IRPFlow.getConveyed; IRPSysMLPort.getType.

**Éléments retournés en échec.** Flow, ports and incompatible Flow Items.

**Dépendances au profil / décisions.** Functional Flow; Flow Item.

**Scénarios de validation.** port mismatch; conveyed mismatch; coherent.

**Implémentation.** Adaptateur `FunctionalFlowItemTypeCoherenceChecker` → `CheckerRuleService.checkFunctionalFlowItemTypeCoherence()`.

### 207668 - FunctionalFlowPortStereotypeChecker

**Exigence.** The checker shall detect and report any Functional Flow connected to a port that is not stereotyped as Flow Port.

**Applicabilité.** Functional Flow - Error.

**Responsabilité.** Functional Flow

**Algorithme.** Same endpoint new-term validation as 206470, restricted to Functional Flow.

**API Rhapsody utilisée.** IRPFlow endpoints; new-term resolution.

**Éléments retournés en échec.** Flow and invalid ports.

**Dépendances au profil / décisions.** Functional Flow Sources/Targets = Flow Port.

**Scénarios de validation.** native SysMLPort versus Flow Port.

**Implémentation.** Adaptateur `FunctionalFlowPortStereotypeChecker` → `CheckerRuleService.checkFunctionalFlowPortStereotype()`.

### 206468 - LogicalFlowPortDirectionChecker

**Exigence.** The checker shall detect and report any Logical Flow whose connected Flow Port directions are inconsistent with the flow direction.

**Applicabilité.** Logical Flow - Error.

**Responsabilité.** Logical Flow

**Algorithme.** Same exact direction matrix as 353035, restricted to Logical Flow.

**API Rhapsody utilisée.** IRPFlow.getDirection; IRPSysMLPort.getPortDirection.

**Éléments retournés en échec.** Flow and both ports.

**Dépendances au profil / décisions.** Logical Flow Sources/Targets = Flow Port.

**Scénarios de validation.** all exact direction values.

**Implémentation.** Adaptateur `LogicalFlowPortDirectionChecker` → `CheckerRuleService.checkLogicalFlowPortDirection()`.

### 207669 - LogicalFlowItemTypeCoherenceChecker

**Exigence.** The checker shall detect and report any Logical Flow whose connected Flow Ports or conveyed Flow Item use inconsistent Flow Item types.

**Applicabilité.** Logical Flow - Error.

**Responsabilité.** Logical Flow

**Algorithme.** Same type-coherence algorithm as 206469, restricted to Logical Flow.

**API Rhapsody utilisée.** IRPFlow.getConveyed; IRPSysMLPort.getType.

**Éléments retournés en échec.** Flow, ports and incompatible Flow Items.

**Dépendances au profil / décisions.** Logical Flow; Flow Item.

**Scénarios de validation.** mismatched and coherent flows.

**Implémentation.** Adaptateur `LogicalFlowItemTypeCoherenceChecker` → `CheckerRuleService.checkLogicalFlowItemTypeCoherence()`.

### 206499 - LogicalFlowPortStereotypeChecker

**Exigence.** The checker shall detect and report any Logical Flow connected to a port that is not stereotyped as Flow Port.

**Applicabilité.** Logical Flow - Error.

**Responsabilité.** Logical Flow

**Algorithme.** Same endpoint new-term validation as 206470, restricted to Logical Flow.

**API Rhapsody utilisée.** IRPFlow endpoints; new-term resolution.

**Éléments retournés en échec.** Flow and invalid ports.

**Dépendances au profil / décisions.** Logical Flow Sources/Targets = Flow Port.

**Scénarios de validation.** plain SysMLPort and exact Flow Port.

**Implémentation.** Adaptateur `LogicalFlowPortStereotypeChecker` → `CheckerRuleService.checkLogicalFlowPortStereotype()`.

### 208311 - UnclassifiedGenericFlowChecker

**Exigence.** The checker shall detect and report every Generic Flow that has not been specialized as an Operational Flow; Functional Flow; Logical Flow; or Technical Flow.

**Applicabilité.** Generic Flow - Warning.

**Responsabilité.** Generic Flow

**Algorithme.** A Generic Flow is NOK when no specialized new term Operational Flow, Functional Flow, Logical Flow or Technical Flow is also applied.

**API Rhapsody utilisée.** IRPModelElement stereotypes/new-term inspection.

**Éléments retournés en échec.** The unclassified Generic Flow.

**Dépendances au profil / décisions.** Generic Flow and four specialized flow terms.

**Scénarios de validation.** generic only; generic plus each specialization.

**Implémentation.** Adaptateur `UnclassifiedGenericFlowChecker` → `CheckerRuleService.checkUnclassifiedGenericFlow()`.

### 206498 - OperationalContextConnectivityChecker

**Exigence.** The checker shall detect and report any Operational System or Stakeholder represented in a Context Diagram or Environment Diagram that is not linked to another Operational System or Stakeholder by an Operational Flow.

**Applicabilité.** Operational System, Stakeholder - Error.

**Responsabilité.** Operational System and Stakeholder represented in Context/Environment diagrams

**Algorithme.** First require a real semantic representation of the element in a Context Diagram or Environment Diagram. Then search Operational Flows touching one of its owned Flow Ports and an opposite Operational System or Stakeholder. The check is model-based but starts only for represented elements.

**API Rhapsody utilisée.** IRPDiagram.getElementsInDiagram; IRPFlow endpoints; owner-chain traversal.

**Éléments retournés en échec.** The isolated represented element.

**Dépendances au profil / décisions.** DrawingToolbar is not a representation whitelist; existing systems/stakeholders may be added to the diagram.

**Scénarios de validation.** directly represented isolated elements; connected system-system and system-stakeholder.

**Implémentation.** Adaptateur `OperationalContextConnectivityChecker` → `CheckerRuleService.checkOperationalContextConnectivity()`.

### 207822 - OperationalSequenceSystemOfInterestChecker

**Exigence.** The checker shall detect and report any Operational Sequence Diagram that does not contain exactly one Operational System designated as <<SystemOfInterest>>.

**Applicabilité.** Operational Sequence Diagram - Error.

**Responsabilité.** Operational Sequence Diagram

**Algorithme.** Collect represented Operational Systems and retain those carrying stereotype SystemOfInterest. Exactly one distinct system is required.

**API Rhapsody utilisée.** IRPDiagram.getElementsInDiagram; stereotypes.

**Éléments retournés en échec.** The diagram.

**Dépendances au profil / décisions.** SystemOfInterest applies to Class/Operational System, not the lifeline role.

**Scénarios de validation.** zero, one and two SOI systems.

**Implémentation.** Adaptateur `OperationalSequenceSystemOfInterestChecker` → `CheckerRuleService.checkOperationalSequenceSystemOfInterest()`.

### 210753 - OperationalSequenceFlowTypingChecker

**Exigence.** The checker shall detect and report any DataFlow message in an Operational Sequence Diagram that has no dependency to a Flow Item, or whose associated Flow Item is not conveyed by an Operational Flow connecting the Operational Systems represented by the source and target lifelines.

**Applicabilité.** Operational Sequence Diagram - Error.

**Responsabilité.** Every DataFlow in an Operational Sequence Diagram

**Algorithme.** For each DataFlow, find dependencies whose target is a Flow Item. At least one is required. Resolve source/target lifeline Operational Systems, then require an Operational Flow between those systems conveying the same Flow Item. Message/flow direction is intentionally outside this checker.

**API Rhapsody utilisée.** IRPCollaboration.getMessages; IRPMessage.getSource/getTarget; IRPDependency.getDependent/getDependsOn; IRPFlow.getConveyed.

**Éléments retournés en échec.** Diagram, DataFlow and unmatched Flow Items.

**Dépendances au profil / décisions.** The Safran sequence plugin stores message-to-Flow Item association as a Dependency.

**Scénarios de validation.** no dependency; dependency to unconveyed item; coherent DataFlow and Operational Flow.

**Implémentation.** Adaptateur `OperationalSequenceFlowTypingChecker` → `CheckerRuleService.checkOperationalSequenceFlowTyping()`.

### 210761 - UnconnectedOperationalFlowPortChecker

**Exigence.** The checker shall detect and report any Flow Port owned by an Operational System or Stakeholder that is not connected to an Operational Flow.

**Applicabilité.** Flow Port - Warning.

**Responsabilité.** Flow Port owned by Operational System or Stakeholder

**Algorithme.** Search Operational Flows in the surrounding model and pass when one uses the exact port as end1 or end2.

**API Rhapsody utilisée.** IRPSysMLPort owner; project element traversal; IRPFlow endpoints.

**Éléments retournés en échec.** The unconnected Flow Port.

**Dépendances au profil / décisions.** Only operational owners are applicable.

**Scénarios de validation.** unconnected and connected operational ports.

**Implémentation.** Adaptateur `UnconnectedOperationalFlowPortChecker` → `CheckerRuleService.checkUnconnectedOperationalFlowPort()`.

### 210762 - UnconnectedFunctionalFlowPortChecker

**Exigence.** The checker shall detect and report any Flow Port owned by a Function that is not connected to a Functional Flow.

**Applicabilité.** Flow Port - Warning.

**Responsabilité.** Flow Port owned by Function

**Algorithme.** Same connection search as 210761, restricted to Functional Flow.

**API Rhapsody utilisée.** IRPSysMLPort owner; IRPFlow endpoints.

**Éléments retournés en échec.** The unconnected Flow Port.

**Dépendances au profil / décisions.** Function + Functional Flow.

**Scénarios de validation.** unconnected and connected functional ports.

**Implémentation.** Adaptateur `UnconnectedFunctionalFlowPortChecker` → `CheckerRuleService.checkUnconnectedFunctionalFlowPort()`.

### 210763 - UnconnectedLogicalFlowPortChecker

**Exigence.** The checker shall detect and report any Flow Port owned by a Logical System that is not connected to a Logical Flow.

**Applicabilité.** Flow Port - Warning.

**Responsabilité.** Flow Port owned by Logical System

**Algorithme.** Same connection search as 210761, restricted to Logical Flow.

**API Rhapsody utilisée.** IRPSysMLPort owner; IRPFlow endpoints.

**Éléments retournés en échec.** The unconnected Flow Port.

**Dépendances au profil / décisions.** Logical System + Logical Flow.

**Scénarios de validation.** unconnected and connected logical ports.

**Implémentation.** Adaptateur `UnconnectedLogicalFlowPortChecker` → `CheckerRuleService.checkUnconnectedLogicalFlowPort()`.

### 206481 - UnusedFlowItemChecker

**Exigence.** The checker shall detect and report any Flow Item that is not used to type a Flow Port and is not conveyed by any Generic Flow; Operational Flow; Functional Flow; Logical Flow; or Technical Flow.

**Applicabilité.** Flow Item - Warning.

**Responsabilité.** Flow Item

**Algorithme.** Pass when the Flow Item types any Flow Port or appears in the conveyed collection of Generic/Operational/Functional/Logical/Technical Flow. Otherwise NOK.

**API Rhapsody utilisée.** IRPSysMLPort.getType; IRPFlow.getConveyed; project traversal.

**Éléments retournés en échec.** The unused Flow Item.

**Dépendances au profil / décisions.** Flow Item may be used as type and/or conveyed element.

**Scénarios de validation.** unused; port type; conveyed by each flow family.

**Implémentation.** Adaptateur `UnusedFlowItemChecker` → `CheckerRuleService.checkUnusedFlowItem()`.

### 206486 - UnnestedFunctionChecker

**Exigence.** The checker shall detect and report any Function displayed in a Functional Behavior Diagram that has not been transformed through the Nest Function operation.

**Applicabilité.** Function - Warning.

**Responsabilité.** Function represented in Functional Behavior Diagram

**Algorithme.** A represented Function is accepted when nested under another Function or when an explicit Nested/IsNested marker is present. Unrepresented Functions are not applicable.

**API Rhapsody utilisée.** diagram semantic representation; IRPModelElement.getOwner; tag/property lookup.

**Éléments retournés en échec.** The un-nested Function.

**Dépendances au profil / décisions.** Function.Aggregates includes Function.

**Scénarios de validation.** represented root Function; nested Function; explicit marker.

**Implémentation.** Adaptateur `UnnestedFunctionChecker` → `CheckerRuleService.checkUnnestedFunction()`.

### 207924 - FunctionWithReferenceCompatibilityChecker

**Exigence.** The checker shall detect and report any Function With Reference that does not reference exactly one Function, does not preserve the referenced Flow Ports, introduces a Flow Port that does not redefine a referenced Flow Port, or changes the direction or Flow Item type of a redefined Flow Port.

**Applicabilité.** Function With Reference - Error.

**Responsabilité.** Function With Reference

**Algorithme.** Require exactly one References Function generalization targeting a Function. Every local Flow Port must redefine exactly one referenced Flow Port. Only the local name may differ; direction and Flow Item type must be identical. Referenced ports must remain available. Tags and extra stereotypes are ignored.

**API Rhapsody utilisée.** IRPGeneralization.getBaseClass/getDerivedClass; IRPClassifier.getPorts; IRPModelElement.getRedefines; IRPSysMLPort direction/type.

**Éléments retournés en échec.** With Reference element, reference relation and referenced Function; detailed differences in message.

**Dépendances au profil / décisions.** Old name Function Reuse is removed. Profile must allow References Function and local Flow Port.

**Scénarios de validation.** missing reference; added port; direction/type mismatch; rename-only; no local override.

**Implémentation.** Adaptateur `FunctionWithReferenceCompatibilityChecker` → `CheckerRuleService.checkFunctionWithReferenceCompatibility()`.

### 206479 - LogicalSystemBreakdownRepresentationChecker

**Exigence.** The checker shall detect and report any Logical System that is not represented in at least one Logical Breakdown Structure.

**Applicabilité.** Logical System - Warning.

**Responsabilité.** Logical System

**Algorithme.** Pass when the exact element is semantically represented in at least one Logical Breakdown Structure.

**API Rhapsody utilisée.** project diagram traversal; IRPDiagram.getElementsInDiagram.

**Éléments retournés en échec.** The unrepresented Logical System.

**Dépendances au profil / décisions.** Logical Breakdown Structure can represent existing Logical Systems.

**Scénarios de validation.** unrepresented and represented.

**Implémentation.** Adaptateur `LogicalSystemBreakdownRepresentationChecker` → `CheckerRuleService.checkLogicalSystemBreakdownRepresentation()`.

### 206487 - UnnestedLogicalSystemChecker

**Exigence.** The checker shall detect and report any Logical System displayed in a Logical Architecture Diagram that has not been transformed through the Nest Logical System operation.

**Applicabilité.** Logical System - Warning.

**Responsabilité.** Logical System represented in Logical Architecture Diagram

**Algorithme.** Same nesting convention as 206486: owner Logical System or explicit Nested marker.

**API Rhapsody utilisée.** diagram representation; owner; tag/property.

**Éléments retournés en échec.** The un-nested Logical System.

**Dépendances au profil / décisions.** Logical System.Aggregates includes Logical System.

**Scénarios de validation.** root represented; nested; marker.

**Implémentation.** Adaptateur `UnnestedLogicalSystemChecker` → `CheckerRuleService.checkUnnestedLogicalSystem()`.

### 207925 - LogicalSystemWithReferenceCompatibilityChecker

**Exigence.** The checker shall detect and report any Logical System With Reference that does not reference exactly one Logical System, does not preserve the referenced Flow Ports, introduces a Flow Port that does not redefine a referenced Flow Port, or changes the direction or Flow Item type of a redefined Flow Port.

**Applicabilité.** Logical System With Reference - Error.

**Responsabilité.** Logical System With Reference

**Algorithme.** Require exactly one References Logical System targeting only a Logical System. Local Flow Ports must redefine referenced ports; only names may differ. Direction and Flow Item type remain equal. No comparison of Functions is performed. Tags/stereotypes are ignored.

**API Rhapsody utilisée.** Generalization endpoints; classifier ports; port redefines; direction/type.

**Éléments retournés en échec.** Reference element/relation/target with detailed differences.

**Dépendances au profil / décisions.** Operational System is not a valid reference target. Redefines System remains a separate, more permissive concept.

**Scénarios de validation.** missing reference; Operational System target; extra port; mismatch; rename-only.

**Implémentation.** Adaptateur `LogicalSystemWithReferenceCompatibilityChecker` → `CheckerRuleService.checkLogicalSystemWithReferenceCompatibility()`.

### 206480 - TechnicalComponentBreakdownRepresentationChecker

**Exigence.** The checker shall detect and report any Technical Component that is not represented in at least one Technical Breakdown Diagram.

**Applicabilité.** Technical Component - Warning.

**Responsabilité.** Technical Component

**Algorithme.** Pass when represented in at least one Technical Breakdown Diagram.

**API Rhapsody utilisée.** diagram traversal and semantic element resolution.

**Éléments retournés en échec.** The unrepresented Technical Component.

**Dépendances au profil / décisions.** Technical Breakdown Diagram.

**Scénarios de validation.** represented and unrepresented.

**Implémentation.** Adaptateur `TechnicalComponentBreakdownRepresentationChecker` → `CheckerRuleService.checkTechnicalComponentBreakdownRepresentation()`.

### 206488 - UnnestedTechnicalComponentChecker

**Exigence.** The checker shall detect and report any Technical Component displayed in a Technical Architecture Diagram that has not been transformed through the Nest Technical Component operation.

**Applicabilité.** Technical Component - Warning.

**Responsabilité.** Technical Component represented in Technical Architecture Diagram

**Algorithme.** Same nesting convention as 206486: owner Technical Component or explicit Nested marker.

**API Rhapsody utilisée.** diagram representation; owner; markers.

**Éléments retournés en échec.** The un-nested Technical Component.

**Dépendances au profil / décisions.** Technical Component.Aggregates includes Technical Component.

**Scénarios de validation.** root, nested, marker.

**Implémentation.** Adaptateur `UnnestedTechnicalComponentChecker` → `CheckerRuleService.checkUnnestedTechnicalComponent()`.

### 207927 - TechnicalComponentWithReferenceCompatibilityChecker

**Exigence.** The checker shall detect and report any Technical Component With Reference that does not reference exactly one Technical Component, does not preserve the referenced Technical Ports, introduces a Technical Port that does not redefine a referenced Technical Port, or changes the port contract of a redefined Technical Port.

**Applicabilité.** Technical Component With Reference - Error.

**Responsabilité.** Technical Component With Reference

**Algorithme.** Require exactly one References Technical Component targeting a Technical Component. Every local Technical Port must redefine one referenced Technical Port. The port name may differ; the port contract must remain identical. Tags and extra stereotypes are ignored. Logical Systems are not compared.

**API Rhapsody utilisée.** Generalization endpoints; IRPClassifier.getPorts; IRPPort.getContract; getRedefines.

**Éléments retournés en échec.** Reference element/relation/target and contract differences.

**Dépendances au profil / décisions.** Technical Component never contains Logical System; System Realization is a dependency to Logical System.

**Scénarios de validation.** missing reference; added port; contract mismatch; rename-only; no override.

**Implémentation.** Adaptateur `TechnicalComponentWithReferenceCompatibilityChecker` → `CheckerRuleService.checkTechnicalComponentWithReferenceCompatibility()`.

### 206485 - UseCaseDiagramRepresentationChecker

**Exigence.** The checker shall detect and report any Use_Case that is not represented in at least one Use_Case Diagram contained directly in a Lifecycle Phase.

**Applicabilité.** Use_Case - Warning.

**Responsabilité.** Use_Case

**Algorithme.** Search Use_Case Diagrams that directly belong to a Lifecycle Phase and verify a real semantic representation of the Use_Case. Diagram ownership by the Use_Case is not accepted.

**API Rhapsody utilisée.** IRPDiagram.getElementsInDiagram; IRPModelElement.getOwner.

**Éléments retournés en échec.** The unrepresented Use_Case.

**Dépendances au profil / décisions.** Use_Case is absent from DrawingToolbar because it cannot be created there, but an existing Use_Case can be represented.

**Scénarios de validation.** not represented; represented in valid diagram; represented in diagram with wrong owner.

**Implémentation.** Adaptateur `UseCaseDiagramRepresentationChecker` → `CheckerRuleService.checkUseCaseDiagramRepresentation()`.

### 206493 - UseCaseIncludeExtendTargetChecker

**Exigence.** The checker shall detect and report any UML include or extend relationship whose source is a Use_Case and whose target is not another Use_Case.

**Applicabilité.** Use_Case - Warning.

**Responsabilité.** Use_Case source of include/extend

**Algorithme.** Inspect generalizations/dependencies/relations carrying exact predefined include or extend stereotypes. When the source is the checked Use_Case, the target must also be Use_Case.

**API Rhapsody utilisée.** relations/dependencies; stereotype name; opposite endpoint.

**Éléments retournés en échec.** Use_Case and invalid relation/target.

**Dépendances au profil / décisions.** PredefinedTypes.sbs include and extend stereotypes.

**Scénarios de validation.** valid target; non-UseCase target; unrelated relations ignored.

**Implémentation.** Adaptateur `UseCaseIncludeExtendTargetChecker` → `CheckerRuleService.checkUseCaseIncludeExtendTarget()`.

### 206482 - UseCaseSatisfactionChecker

**Exigence.** The checker shall detect and report any Use_Case that is not linked by Satisfies to at least one Function.

**Applicabilité.** Use_Case - Warning.

**Responsabilité.** Use_Case

**Algorithme.** Pass when a Satisfies dependency has Function as dependent/source and the checked Use_Case as dependsOn/target.

**API Rhapsody utilisée.** IRPDependency endpoints and new term.

**Éléments retournés en échec.** The unsatisfied Use_Case.

**Dépendances au profil / décisions.** Satisfies Sources include Function; Target = Use_Case.

**Scénarios de validation.** none, reversed, valid.

**Implémentation.** Adaptateur `UseCaseSatisfactionChecker` → `CheckerRuleService.checkUseCaseSatisfaction()`.

### 206483 - UseCaseExternalActorAssociationChecker

**Exigence.** The checker shall detect and report any Use_Case that is not connected to at least one Stakeholder or Operational System by a Directed Association or a standard UML Association.

**Applicabilité.** Use_Case - Warning.

**Responsabilité.** Use_Case

**Algorithme.** Inspect only standard UML Association or Directed Association. Pass when the opposite classifier is Stakeholder or Operational System. Other generic IRPRelation types are ignored.

**API Rhapsody utilisée.** IRPRelation endpoints/type/metaclass/new term.

**Éléments retournés en échec.** The unassociated Use_Case.

**Dépendances au profil / décisions.** Both directed and non-directed associations are accepted by decision.

**Scénarios de validation.** no association; Directed Association; UML Association; other relation.

**Implémentation.** Adaptateur `UseCaseExternalActorAssociationChecker` → `CheckerRuleService.checkUseCaseExternalActorAssociation()`.

### 206495 - OperationalSequenceFlowPresenceChecker

**Exigence.** The checker shall detect and report any Operational Sequence Diagram containing at least two represented Operational Systems when no DataFlow message is exchanged between their lifelines.

**Applicabilité.** Operational Sequence Diagram - Warning.

**Responsabilité.** Operational Sequence Diagram

**Algorithme.** Resolve all DataFlow messages. Pass when at least one message connects classifier roles whose formal classifiers are two distinct Operational Systems. If fewer than two systems are represented, the checker is not applicable.

**API Rhapsody utilisée.** IRPCollaboration.getMessages; IRPMessage endpoints; IRPClassifierRole formal classifier.

**Éléments retournés en échec.** The sequence diagram.

**Dépendances au profil / décisions.** This checker tests exchange presence only; Flow Item coherence is delegated to 210753.

**Scénarios de validation.** two systems/no DataFlow; DataFlow between distinct systems; roles of same system.

**Implémentation.** Adaptateur `OperationalSequenceFlowPresenceChecker` → `CheckerRuleService.checkOperationalSequenceFlowPresence()`.

### 206496 - OperationalSequenceConditionMarkChecker

**Exigence.** The checker shall detect and report any Condition Mark in an Operational Sequence Diagram whose formal type is not an Operating Mode owned by the Operational System designated as <<SystemOfInterest>>.

**Applicabilité.** Operational Sequence Diagram - Warning.

**Responsabilité.** Condition Marks in Operational Sequence Diagram

**Algorithme.** Require exactly one represented SOI system when Condition Marks exist. Every Condition Mark getFormalType() must be an Operating Mode whose owner chain contains that SOI system.

**API Rhapsody utilisée.** IRPMessage.getFormalType; message kind identification; owner-chain traversal.

**Éléments retournés en échec.** Diagram and invalid Condition Marks.

**Dépendances au profil / décisions.** No artificial dependency from Condition Mark to mode.

**Scénarios de validation.** unassigned; mode of another system; SOI mode.

**Implémentation.** Adaptateur `OperationalSequenceConditionMarkChecker` → `CheckerRuleService.checkOperationalSequenceConditionMark()`.

### 206489 - OperationalSequenceActionBlockChecker

**Exigence.** The checker shall detect and report any Action Block in an Operational Sequence Diagram that references a Function not owned by its lifeline Operational System, or whose Function is not available through Activated Function in the current Operating Mode defined by the last preceding Condition Mark on the same lifeline.

**Applicabilité.** Operational Sequence Diagram - Warning.

**Responsabilité.** Action Blocks in Operational Sequence Diagram

**Algorithme.** For each Action Block whose formal type is a Function, require ownership by the Operational System of the action lifeline. Order messages by sequence number and find the last preceding Condition Mark on the same lifeline. If none exists, return OK for mode availability. Otherwise require Activated Function dependency from current Operating Mode to Function.

**API Rhapsody utilisée.** IRPMessage.getFormalType/getSequenceNumber/getSource/getTarget; IRPClassifierRole; IRPDependency.

**Éléments retournés en échec.** Diagram, Action Block, Function, Condition Mark and mode as relevant.

**Dépendances au profil / décisions.** Activated Function is not represented in the sequence diagram.

**Scénarios de validation.** wrong system; no current mode OK; unavailable in mode; available in mode.

**Implémentation.** Adaptateur `OperationalSequenceActionBlockChecker` → `CheckerRuleService.checkOperationalSequenceActionBlocks()`.

### 206490 - LifecyclePhaseRepresentationChecker

**Exigence.** The checker shall detect and report any Lifecycle Phase that is not represented in at least one Lifecycle Diagram.

**Applicabilité.** Lifecycle Phase - Warning.

**Responsabilité.** Lifecycle Phase

**Algorithme.** Pass when the phase is represented in at least one Lifecycle Diagram.

**API Rhapsody utilisée.** diagram traversal and semantic representation.

**Éléments retournés en échec.** The unrepresented phase.

**Dépendances au profil / décisions.** Lifecycle Diagram.

**Scénarios de validation.** represented/unrepresented.

**Implémentation.** Adaptateur `LifecyclePhaseRepresentationChecker` → `CheckerRuleService.checkLifecyclePhaseRepresentation()`.

### 206491 - LifecyclePhaseConnectivityChecker

**Exigence.** The checker shall detect and report any Lifecycle Phase that is not connected by a Lifecycle Phase Transition to at least one Start Phase, Lifecycle Phase, or End Phase.

**Applicabilité.** Lifecycle Phase - Warning.

**Responsabilité.** Lifecycle Phase

**Algorithme.** Pass when any Lifecycle Phase Transition connects the phase to Start Phase, Lifecycle Phase or End Phase. A single phase between Start and End is valid.

**API Rhapsody utilisée.** IRPDependency endpoints; new terms.

**Éléments retournés en échec.** The isolated Lifecycle Phase.

**Dépendances au profil / décisions.** Sources: Start Phase/Lifecycle Phase; Targets: Lifecycle Phase/End Phase.

**Scénarios de validation.** isolated; Start→Phase→End.

**Implémentation.** Adaptateur `LifecyclePhaseConnectivityChecker` → `CheckerRuleService.checkLifecyclePhaseConnectivity()`.

### 206503 - OperatingModeRepresentationChecker

**Exigence.** The checker shall detect and report any Operating Mode that is not represented in at least one Operating Mode Chart.

**Applicabilité.** Operating Mode - Warning.

**Responsabilité.** Operating Mode

**Algorithme.** Pass when represented in at least one Operating Mode Chart.

**API Rhapsody utilisée.** diagram representation.

**Éléments retournés en échec.** The unrepresented mode.

**Dépendances au profil / décisions.** Operating Mode Chart.

**Scénarios de validation.** represented/unrepresented.

**Implémentation.** Adaptateur `OperatingModeRepresentationChecker` → `CheckerRuleService.checkOperatingModeRepresentation()`.

### 206492 - OperatingModeTransitionChecker

**Exigence.** The checker shall detect and report any Operating Mode that does not have at least one incoming transition and at least one outgoing transition.

**Applicabilité.** Operating Mode - Error.

**Responsabilité.** Operating Mode

**Algorithme.** Require at least one incoming and one outgoing transition. The checker uses transition source/target identity and accepts self-transitions as both incoming and outgoing.

**API Rhapsody utilisée.** IRPStateVertex transitions; IRPTransition endpoints.

**Éléments retournés en échec.** The mode lacking one direction.

**Dépendances au profil / décisions.** Operating Mode transitions.

**Scénarios de validation.** none, incoming-only, outgoing-only, both.

**Implémentation.** Adaptateur `OperatingModeTransitionChecker` → `CheckerRuleService.checkOperatingModeTransitions()`.

### 206505 - OperatingModeChartInitialStateChecker

**Exigence.** The checker shall detect and report any Operating Mode Chart that has neither an initial pseudostate nor a default transition.

**Applicabilité.** Operating Mode Chart - Warning.

**Responsabilité.** Operating Mode Chart

**Algorithme.** Pass when an initial connector/state is detected or a DefaultTransition exists. DefaultTransition is the preferred profile construction.

**API Rhapsody utilisée.** IRPStatechart root; state vertices/transitions/connectors.

**Éléments retournés en échec.** The chart.

**Dépendances au profil / décisions.** Operating Mode Chart DrawingToolbar includes DefaultTransition.

**Scénarios de validation.** no initial/default; default transition; native initial edge case.

**Implémentation.** Adaptateur `OperatingModeChartInitialStateChecker` → `CheckerRuleService.checkOperatingModeChartInitialState()`.

### 206504 - OperatingModeChartFinalStateChecker

**Exigence.** The checker shall detect and report any Operating Mode Chart that does not contain a TerminationState.

**Applicabilité.** Operating Mode Chart - Warning.

**Responsabilité.** Operating Mode Chart

**Algorithme.** Pass only when a TerminationState is present in the chart state hierarchy. Exact profile/metaclass detection is preferred over text matching.

**API Rhapsody utilisée.** IRPStatechart root nested vertices; connector/state metaclass/new term.

**Éléments retournés en échec.** The chart.

**Dépendances au profil / décisions.** TerminationState from profile.

**Scénarios de validation.** without termination; compliant chart; unreachable termination remains out of scope.

**Implémentation.** Adaptateur `OperatingModeChartFinalStateChecker` → `CheckerRuleService.checkOperatingModeChartFinalState()`.

### 206501 - OperationalSystemDiagramRepresentationChecker

**Exigence.** The checker shall detect and report any Operational System that is not represented in at least one Context Diagram or Environment Diagram.

**Applicabilité.** Operational System - Warning.

**Responsabilité.** Operational System

**Algorithme.** Pass when the exact existing system is represented in any Context Diagram or Environment Diagram. Representation is direct; Flow Port owner inference is not a substitute for this requirement.

**API Rhapsody utilisée.** diagram traversal; semantic element identity.

**Éléments retournés en échec.** The unrepresented Operational System.

**Dépendances au profil / décisions.** DrawingToolbar absence does not forbid representation of an existing system.

**Scénarios de validation.** unrepresented; direct context; direct environment.

**Implémentation.** Adaptateur `OperationalSystemDiagramRepresentationChecker` → `CheckerRuleService.checkOperationalSystemDiagramRepresentation()`.

### 206484 - ConnectedFlowPortNameChecker

**Exigence.** The checker shall detect and report; as information; any pair of Flow Ports connected by an Operational Flow; Functional Flow; Logical Flow; or Technical Flow whose names differ.

**Applicabilité.** Operational Flow, Functional Flow, Logical Flow, Technical Flow - Info.

**Responsabilité.** Operational/Functional/Logical/Technical Flow

**Algorithme.** When both endpoints resolve to Flow Ports, compare names exactly. A mismatch returns an informational finding; missing endpoints are ignored.

**API Rhapsody utilisée.** IRPFlow endpoints; IRPSysMLPort.getName.

**Éléments retournés en échec.** Flow and both differently named ports.

**Dépendances au profil / décisions.** Applies to four specialized flow new terms.

**Scénarios de validation.** same and different names.

**Implémentation.** Adaptateur `ConnectedFlowPortNameChecker` → `CheckerRuleService.checkConnectedFlowPortNames()`.

## 7. Générateur de modèles de validation v8

- Création profilée par le nom exact du new term et validation préalable de `Aggregates`.
- Relations Flow/Dependency stockées depuis la source ou son premier propriétaire autorisé.
- Generalizations `References ...` créées sous l’élément `With Reference`; validité des extrémités contrôlée par le checker.
- `Use_Case Diagram` créé directement sous une `Lifecycle Phase`; le Use_Case existant est ajouté comme représentation.
- DataFlow associé au Flow Item par une dependency message → Flow Item.
- ActionBlock et ConditionMark utilisent `setFormalType(Function/Operating Mode)`.
- DrawingToolbar validé uniquement pour les opérations de création depuis la toolbar (`DataFlow`, `Condition Mark`, `SDActionBlock`).

## 8. Incohérences de profil à corriger avant exécution complète

- `References Logical System.Targets` doit être limité à `Logical System`.
- `References Technical Component.Sources` doit utiliser `Technical Component With Reference` et non l’ancien nom.
- Les éléments `With Reference` doivent autoriser le port local nécessaire à une redéfinition dans `Aggregates`.
- `System Realization.Sources` doit remplacer l’ancien nom `Technical Component Reference`.
- `Redefines System.Sources` doit documenter `Logical System`; ses cibles peuvent être `Operational System, Logical System`.

## 9. Validation et limites

- Compilation syntaxique Java 17 validée avec des stubs générés depuis la Javadoc `java_api.zip`.
- La compilation et le test runtime finaux doivent être effectués avec le véritable `Rhapsody.jar` 10.0.2 et un modèle chargé avec le profil corrigé.
- Les parcours de projet peuvent être coûteux sur de très grands modèles ; une indexation par GUID/new term est une évolution possible.
- La disponibilité des ports hérités des éléments `With Reference` dépend du comportement de `IRPClassifier.getPorts()` dans le modèle réel ; les scénarios v8 couvrent ce point.
