# SafranChecker plugin

Generated implementation of the **40 Safran external checkers** for IBM Rhapsody 10.0.2 / Java 17.

## Architecture

- `SafranCheckerPlugin` loads `safran-checkers.xml` and dynamically instantiates enabled classes from package `checkers`.
- `SafranChecker` supplies configuration-backed implementations of `getDomain()`, `getMetaclasses()`, `getName()`, `getSeverity()`, and `getShouldCallFromCG()`.
- each concrete checker implements only `check(...)` and `getCompleteness()`.
- `CheckerRuleService` contains the business rules.
- `RhapsodyModelQueries` centralizes defensive Java API access.

## Build

```powershell
.\build\build.ps1 -RhapsodyHome "C:\Program Files\IBM\Rhapsody\10.0.2"
```

or run `build\build.bat` after setting `RHAPSODY_HOME`. The scripts deliberately do not package IBM `Rhapsody.jar`.

## Deployment

1. Build the project.
2. Copy `target\SafranChecker.jar`, `target\safran-checkers.xml`, and `target\SafranChecker.hep` to the same plugin directory.
3. Load the `.hep` file from Rhapsody or merge its `[Helpers]` declaration into the selected Rhapsody configuration.
4. Restart Rhapsody after modifying the JAR or XML.

## Configuration

Every checker is declared in `safran-checkers.xml`. Set `enabled="false"` to disable a checker. A full restart is required after configuration changes because external checkers are registered during plugin initialization.

## Traceability

- DNG identifier to Java class mapping: `docs/checker-class-mapping.csv`
- API and model assumptions: `docs/API_AND_MODEL_ASSUMPTIONS.md`
- implementation validation: `docs/VALIDATION_REPORT.md`

## Important

Validate the three Nest-operation conventions, SystemOfInterest stereotype location, and ConditionMark assignment on a representative Safran model before industrial deployment. The code is structured so those conventions are changed in one service rather than in 40 classes.
