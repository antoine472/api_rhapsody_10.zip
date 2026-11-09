# API and model assumptions

The implementation uses only methods present in the supplied IBM Rhapsody 10.0.2 Java API Javadoc.

## Explicit model conventions requiring confirmation on a representative Safran model

1. **Nest Function / Nest Logical System / Nest Technical Component**
   The API exposes no dedicated method that proves that the corresponding UI command was executed. The check accepts either:
   - ownership below another element with the same new term, or
   - a boolean property/tag named `SafranChecker.Nested`, `Nested`, or `IsNested`.
   Adapt `CheckerRuleService.checkNestedConvention(...)` after confirming the exact structure created by each Safran profile command.

2. **SystemOfInterest**
   `SystemOfInterest` is not declared as a new term in the supplied profile. The checker therefore searches for a stereotype named exactly `SystemOfInterest` on an Operational System.

3. **Condition mark assignment**
   `IRPConditionMark` has no specific assignment accessor in the supplied Javadoc. Assignment is detected through references or dependencies from the ConditionMark to an Operating Mode.

4. **Flow direction**
   Port directions are the profile values `In`, `Out`, and `InOut`. Empty or unknown `IRPFlow.getDirection()` values are interpreted as end1-to-end2. Reverse and bidirectional strings are normalized defensively.

5. **Sequence-diagram exchanges**
   Operational Flow presence/typing is detected from either a represented `IRPFlow` or an `IRPMessage.getFormalType()` that resolves to an Operational Flow.

6. **Redefinition compatibility**
   Redefines Function/System/Component are treated as `IRPGeneralization`. Derived elements must expose the same named ports/content as the base. Function Flow Ports additionally compare direction and Flow Item type. Technical Ports compare their contract.

These assumptions are isolated in `CheckerRuleService` and `RhapsodyModelQueries` so that validation feedback can be incorporated without changing the plugin/configuration architecture.
