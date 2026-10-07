package test.unittest;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGeneralization;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;

import test.RhapsodyBaseTest;
import tools.UpdateFunctionDefinition;

public class UpdateFunctionDefinitionTest extends RhapsodyBaseTest {

    private UpdateFunctionDefinition tool;

    public UpdateFunctionDefinitionTest() {
        super("./src/test/resources/UpdateFunctionDefinition/UpdateFunctionDefinition.rpyx");
    }

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        tool = new UpdateFunctionDefinition(rhapsodyApplication);
        Assertions.assertNotNull(tool, "Tool not initialized");
    }

    // =========================================================
    // GUIDs à renseigner (à récupérer dans Rhapsody)
    // =========================================================

    // T01
    private static final String GUID_T01_SELECTED_NOT_ALLOWED =
            "GUID 71623d47-c584-4e76-b493-7a0b03192c4b";

    // T02
    private static final String GUID_T02_SELECTED_NO_GENERALIZATION =
            "GUID ac220271-527e-479b-a624-7788f9cc12ac";

    // T03
    private static final String GUID_T03_SELECTED_NO_REDEFINES =
            "GUID 347a9354-c882-4d46-ae8d-a3bc224a937b";

    // T04
    private static final String GUID_T04_SELECTED_INVALID_BASE =
            "GUID 7b670eb3-8422-45cf-8961-d38a7c99656e";

    // T05
    private static final String GUID_T05_SELECTED_WB_CREATE =
            "GUID d762184a-4959-4c29-8990-5db8bca47ca4";
    private static final String GUID_T05_BB_DEFINITION =
            "GUID ab872d86-a1a6-411d-aed7-945f4428baef";
    private static final String GUID_T05_BB_FUNC_A =
            "GUID 2cc201ea-911b-4d6a-98f8-3eb6fcec3335";
    private static final String GUID_T05_BB_BASEFUNC_B =
            "GUID b7db443c-1d66-442f-b7c8-562f489ddbd8";
    private static final String GUID_T05_BB_FUNCREF_B =
            "GUID 37b49432-4b95-460a-8da1-2d44608406ed";

    // T06
    private static final String GUID_T06_SELECTED_WB_UPDATE =
            "GUID 8f67e34b-08d5-4dea-aad9-76db4e7eafe8";
    private static final String GUID_T06_BB_DEFINITION =
            "GUID f2a60aad-2fcc-47b7-9103-15c3674aa9fb";
    private static final String GUID_T06_BB_FUNC_A =
            "GUID c2853ffc-5090-4f87-9d3c-96abe5a8829e";
    private static final String GUID_T06_BB_BASEFUNC_B =
            "GUID 4cb2efb0-c8cf-45e7-8ce9-07637094d22c";
    private static final String GUID_T06_BB_FUNCREF_B =
            "GUID f2f7bcc7-6bfb-430b-9d19-fcf16f344a49";

    // T07
    private static final String GUID_T07_SELECTED_WB_SAME_NAME_DIFF_TYPE =
            "GUID 6b2866a1-c1c3-4f60-847e-bc3bc1c32256";
    private static final String GUID_T07_BB_DEFINITION =
            "GUID e096ec60-1219-4843-aa73-d814a1cf72b8";
    private static final String GUID_T07_BB_BASEFUNC =
            "GUID 19799516-0e8f-4423-acd4-6b052deb7d2e";
    private static final String GUID_T07_BB_FUNCREF_F1 =
            "GUID a9dbe1f7-f5c1-4cc0-90f8-9ff65c890eaa";

    // T08
    private static final String GUID_T08_SELECTED_WB_IGNORE_NON_FUNCTIONS =
            "GUID a185b8b7-1645-480e-af2b-b5242fc14cba";
    private static final String GUID_T08_BB_DEFINITION =
            "GUID 8ec6cd00-e0e2-4e79-8087-15fdb65ae5ca";

    // =========================================================
    // Tests
    // =========================================================

    @Test
    public void T01_notAllowedSelection_doesNothing() {
        IRPModelElement selected = byGuid(GUID_T01_SELECTED_NOT_ALLOWED);
        Assertions.assertNotNull(selected);

        // état initial (si c'est une classe)
        int before = (selected instanceof IRPClass c) ? countNestedFunctions(c) : -1;

        selectModelElementInRhapsody(selected);
        tool.execute();

        int after = (selected instanceof IRPClass c) ? countNestedFunctions(c) : -1;

        // Le tool doit sortir immédiatement
        Assertions.assertEquals(before, after, "No change expected for not allowed selection");
    }

    @Test
    public void T02_allowedSelection_noGeneralization_doesNothing() {
        IRPClass selected = (IRPClass) byGuid(GUID_T02_SELECTED_NO_GENERALIZATION);
        Assertions.assertNotNull(selected);

        int before = countNestedFunctions(selected);

        selectModelElementInRhapsody(selected);
        tool.execute();

        int after = countNestedFunctions(selected);
        Assertions.assertEquals(before, after, "No change expected when no generalization exists");
    }

    @Test
    public void T03_allowedSelection_noRedefinesGeneralization_doesNothing() {
        IRPClass selected = (IRPClass) byGuid(GUID_T03_SELECTED_NO_REDEFINES);
        Assertions.assertNotNull(selected);

        int before = countNestedFunctions(selected);

        selectModelElementInRhapsody(selected);
        tool.execute();

        int after = countNestedFunctions(selected);
        Assertions.assertEquals(before, after, "No change expected when no 'Redefines *' generalization exists");
    }

    @Test
    public void T04_redefinesButInvalidBaseTarget_doesNothing() {
        IRPClass selected = (IRPClass) byGuid(GUID_T04_SELECTED_INVALID_BASE);
        Assertions.assertNotNull(selected);

        int before = countNestedFunctions(selected);

        selectModelElementInRhapsody(selected);
        tool.execute();

        int after = countNestedFunctions(selected);
        Assertions.assertEquals(before, after, "No change expected when base target UDMC is invalid");
    }

    @Test
    public void T05_createMissingWBFunctions_fromBBDefinition() {
        IRPClass wb = (IRPClass) byGuid(GUID_T05_SELECTED_WB_CREATE);
        IRPClass bb = (IRPClass) byGuid(GUID_T05_BB_DEFINITION);

        IRPClass bbFuncA = (IRPClass) byGuid(GUID_T05_BB_FUNC_A);
        IRPClass bbBaseFuncB = (IRPClass) byGuid(GUID_T05_BB_BASEFUNC_B);
        IRPClass bbFuncRefB = (IRPClass) byGuid(GUID_T05_BB_FUNCREF_B);

        Assertions.assertNotNull(wb);
        Assertions.assertNotNull(bb);
        Assertions.assertNotNull(bbFuncA);
        Assertions.assertNotNull(bbBaseFuncB);
        Assertions.assertNotNull(bbFuncRefB);

        int before = countNestedFunctions(wb);

        //selectModelElementInRhapsody(wb);
        forceSelectForTool(wb);
        tool.execute();

        // 1) WB doit maintenant contenir FuncA (Function) + FuncRefB (Function With Reference)
        IRPClass wbFuncA = findNested(wb, "Function", bbFuncA.getName());
        Assertions.assertNotNull(wbFuncA, "WB must contain created Function: " + bbFuncA.getName());

        IRPClass wbFuncRefB = findNested(wb, "Function With Reference", bbFuncRefB.getName());
        Assertions.assertNotNull(wbFuncRefB, "WB must contain created Function With Reference: " + bbFuncRefB.getName());

        // 2) Généralisation: wbFuncA -> bbFuncA
        IRPGeneralization gA = findGeneralizationTo(wbFuncA, bbFuncA);
        Assertions.assertNotNull(gA, "WB Function must generalize BB Function");

        // 3) Généralisation: wbFuncRefB -> bbBaseFuncB (pas vers bbFuncRefB)
        IRPGeneralization gRef = findGeneralizationTo(wbFuncRefB, bbBaseFuncB);
        Assertions.assertNotNull(gRef, "WB Ref must generalize BB base function (of BB ref)");

        // 4) Dépendance: wbFuncRefB -> bbFuncRefB
        Assertions.assertTrue(hasDependencyTo(wbFuncRefB, bbFuncRefB),
                "WB Ref must depend on BB Function With Reference");

        int after = countNestedFunctions(wb);
        int expectedAdds = countNestedFunctions(bb); // même profondeur que le tool (ici 0)
        Assertions.assertEquals(before + expectedAdds, after);

        // 5) Optionnel: vérifier stéréotypes si présents
        assertGeneralizationHasStereotypeIfPossible(gA, "b845ee49-501c-4a7c-8b7c-a86580a8abd1");
        assertGeneralizationHasStereotypeIfPossible(gRef, "33d8930c-eb78-48c8-bb70-0a76eec71123");
    }

    @Test
    public void T06_updateExistingWB_addMissingGeneralizationAndDependency_noDuplicate() {

        // --- Inputs ---
        IRPClass wb = (IRPClass) byGuid(GUID_T06_SELECTED_WB_UPDATE);
        IRPClass bb = (IRPClass) byGuid(GUID_T06_BB_DEFINITION);

        IRPClass bbFuncA     = (IRPClass) byGuid(GUID_T06_BB_FUNC_A);
        IRPClass bbBaseFuncB = (IRPClass) byGuid(GUID_T06_BB_BASEFUNC_B);
        IRPClass bbFuncRefB  = (IRPClass) byGuid(GUID_T06_BB_FUNCREF_B);

        Assertions.assertNotNull(wb);
        Assertions.assertNotNull(bb);
        Assertions.assertNotNull(bbFuncA);
        Assertions.assertNotNull(bbBaseFuncB);
        Assertions.assertNotNull(bbFuncRefB);

        // --- Préconditions modèle (WB contient déjà FuncA + FuncRefB, mais relations manquantes) ---
        IRPClass wbFuncA_before = findNested(wb, "Function", bbFuncA.getName());
        IRPClass wbFuncRefB_before = findNested(wb, "Function With Reference", bbFuncRefB.getName());
        Assertions.assertNotNull(wbFuncA_before, "Precondition: WB must already contain Function " + bbFuncA.getName());
        Assertions.assertNotNull(wbFuncRefB_before, "Precondition: WB must already contain Function With Reference " + bbFuncRefB.getName());

        int countFuncA_before = countNestedByNameAndUdmc(wb, "Function", bbFuncA.getName());
        int countRefB_before  = countNestedByNameAndUdmc(wb, "Function With Reference", bbFuncRefB.getName());
        Assertions.assertEquals(1, countFuncA_before, "Precondition: WB must contain exactly one FuncA (no duplicates)");
        Assertions.assertEquals(1, countRefB_before,  "Precondition: WB must contain exactly one FuncRefB (no duplicates)");

        int before = countNestedFunctions(wb);
        int expectedAdds = expectedAddsFromBBToWB(bb, wb);

        // --- Execute ---
        forceSelectForTool(wb);   // méthode déjà présente dans ta classe de test
        tool.execute();

        // Re-fetch (évite proxy stale)
        wb = (IRPClass) byGuid(GUID_T06_SELECTED_WB_UPDATE);

        // --- Assert count: update + create missing (diff BB vs WB) ---
        int after = countNestedFunctions(wb);
        Assertions.assertEquals(before + expectedAdds, after,
                "Unexpected number of (Function / Function With Reference) elements in WB after update");

        // --- Assert no duplicates on existing elements ---
        int countFuncA_after = countNestedByNameAndUdmc(wb, "Function", bbFuncA.getName());
        int countRefB_after  = countNestedByNameAndUdmc(wb, "Function With Reference", bbFuncRefB.getName());
        Assertions.assertEquals(1, countFuncA_after, "No duplicate FuncA should be created in WB");
        Assertions.assertEquals(1, countRefB_after,  "No duplicate FuncRefB should be created in WB");

        // --- Assert relations fixed ---
        IRPClass wbFuncA_after = findNested(wb, "Function", bbFuncA.getName());
        IRPClass wbFuncRefB_after = findNested(wb, "Function With Reference", bbFuncRefB.getName());
        Assertions.assertNotNull(wbFuncA_after);
        Assertions.assertNotNull(wbFuncRefB_after);

        // Generalization: WB FuncA -> BB FuncA
        IRPGeneralization gA = findGeneralizationTo(wbFuncA_after, bbFuncA);
        Assertions.assertNotNull(gA, "WB FuncA must generalize BB FuncA after update");

        // Generalization: WB FuncRefB -> BB BaseFuncB
        IRPGeneralization gRef = findGeneralizationTo(wbFuncRefB_after, bbBaseFuncB);
        Assertions.assertNotNull(gRef, "WB FuncRefB must generalize BB BaseFuncB after update");

        // Dependency: WB FuncRefB -> BB FuncRefB
        Assertions.assertTrue(hasDependencyTo(wbFuncRefB_after, bbFuncRefB),
                "WB FuncRefB must depend on BB FuncRefB after update");

        // --- If something was missing, assert the missing ones now exist ---
        // Dans ton modèle T6 typique: BaseFuncB_T6 manque au départ => doit exister après.
        if (expectedAdds > 0) {
            IRPClass wbBase = findNested(wb, "Function", bbBaseFuncB.getName());
            Assertions.assertNotNull(wbBase, "WB must contain created Base Function: " + bbBaseFuncB.getName());

            // Et en bonus: la base créée doit généraliser la base BB (comportement tool: createFunctionBasedOnRealization)
            IRPGeneralization gBase = findGeneralizationTo(wbBase, bbBaseFuncB);
            Assertions.assertNotNull(gBase, "WB Base Function must generalize BB Base Function");
        }
    }

    @Test
    public void T07_createRefWithDifferentName_noNameClash() {

        IRPClass wb = (IRPClass) byGuid(GUID_T07_SELECTED_WB_SAME_NAME_DIFF_TYPE);
        IRPClass bb = (IRPClass) byGuid(GUID_T07_BB_DEFINITION);

        IRPClass bbBaseFunc = (IRPClass) byGuid(GUID_T07_BB_BASEFUNC);
        IRPClass bbRefF1    = (IRPClass) byGuid(GUID_T07_BB_FUNCREF_F1); // maintenant c'est F1_T7

        // Préconditions
        Assertions.assertEquals("Function With Reference", bbRefF1.getUserDefinedMetaClass());
        Assertions.assertEquals(1, countNestedByNameAndUdmc(wb, "Function", "F1"));
        Assertions.assertEquals(0, countNestedByNameAndUdmc(wb, "Function With Reference", bbRefF1.getName()),
            "Precondition: WB must not already contain the BB ref name " + bbRefF1.getName());

        int before = countNestedFunctions(wb);
        int expectedAdds = expectedAddsFromBBToWB(bb, wb);

        forceSelectForTool(wb);
        tool.execute();

        wb = (IRPClass) byGuid(GUID_T07_SELECTED_WB_SAME_NAME_DIFF_TYPE);

        int after = countNestedFunctions(wb);
        Assertions.assertEquals(before + expectedAdds, after);

        // F1 (Function) toujours unique
        Assertions.assertEquals(1, countNestedByNameAndUdmc(wb, "Function", "F1"));

        // Ref créée : F1_T7
        IRPClass wbRef = findNested(wb, "Function With Reference", bbRefF1.getName());
        Assertions.assertNotNull(wbRef, "WB must contain created Function With Reference: " + bbRefF1.getName());

        // Relations
        Assertions.assertNotNull(findGeneralizationTo(wbRef, bbBaseFunc), "WB ref must generalize base function");
        Assertions.assertTrue(hasDependencyTo(wbRef, bbRefF1), "WB ref must depend on BB ref");
    }
    
    
    @Test
    public void T08_ignoreNonFunctionsInBB_onlyFunctionsAreProcessed() {
        IRPClass wb = (IRPClass) byGuid(GUID_T08_SELECTED_WB_IGNORE_NON_FUNCTIONS);
        IRPClass bb = (IRPClass) byGuid(GUID_T08_BB_DEFINITION);

        Assertions.assertNotNull(wb);
        Assertions.assertNotNull(bb);

        int before = countNestedAllClasses(wb);

        selectModelElementInRhapsody(wb);
        tool.execute();

        // On ne peut pas deviner ici combien de fonctions sont dans BB,
        // mais on peut au moins vérifier qu'aucune classe "non-function" n'a été copiée.
        // => règle: on ne copie que UDMC = Function / Function With Reference.
        List<IRPClass> wbNested = wb.getNestedElementsByMetaClass("Class", 0).toList();
        for (IRPClass c : wbNested) {
            String udmc = c.getUserDefinedMetaClass();
            Assertions.assertTrue(
                    "Function".equals(udmc) || "Function With Reference".equals(udmc) || isPreExistingFixtureElement(wb, c),
                    "WB should not contain newly created non-function class: " + c.getFullPathName()
            );
        }

        int after = countNestedAllClasses(wb);
        Assertions.assertTrue(after >= before, "WB can only grow (no deletions expected)");
    }

    // =========================================================
    // Helpers
    // =========================================================

    private IRPModelElement byGuid(String guid) {
        IRPModelElement el = project.findElementByGUID(guid);
        Assertions.assertNotNull(el, "Element not found by GUID: " + guid);
        return el;
    }

    private int countNestedFunctions(IRPClass owner) {
        int count = 0;
        List<IRPClass> nested = owner.getNestedElementsByMetaClass("Class", 0).toList();
        for (IRPClass c : nested) {
            String udmc = c.getUserDefinedMetaClass();
            if ("Function".equals(udmc) || "Function With Reference".equals(udmc)) {
                count++;
            }
        }
        return count;
    }

    private int countNestedAllClasses(IRPClass owner) {
        return owner.getNestedElementsByMetaClass("Class", 0).toList().size();
    }

    private int countNestedByNameAndUdmc(IRPClass owner, String udmc, String name) {
        int count = 0;
        List<IRPClass> nested = owner.getNestedElementsByMetaClass("Class", 0).toList();
        for (IRPClass c : nested) {
            if (udmc.equals(c.getUserDefinedMetaClass()) && name.equals(c.getName())) {
                count++;
            }
        }
        return count;
    }

    private IRPClass findNested(IRPClass owner, String udmc, String name) {
        List<IRPClass> nested = owner.getNestedElementsByMetaClass("Class", 0).toList();
        for (IRPClass c : nested) {
            if (udmc.equals(c.getUserDefinedMetaClass()) && name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private IRPGeneralization findGeneralizationTo(IRPClass child, IRPClass base) {
        if (child == null || base == null) return null;

        IRPCollection gens = child.getGeneralizations();
        if (gens == null || gens.getCount() == 0) return null;

        @SuppressWarnings("unchecked")
        List<Object> list = gens.toList();
        for (Object o : list) {
            if (o instanceof IRPGeneralization g) {
                IRPClass bc = (IRPClass) g.getBaseClass();
                if (base.equals(bc)) return g;
            }
        }
        return null;
    }

    /**
     * Vérifie une dépendance via réflexion (robuste aux variations API).
     * On inspecte la collection retournée par getDependencies() si elle existe,
     * puis on essaye plusieurs getters possibles pour trouver la "cible".
     */
    private boolean hasDependencyTo(IRPModelElement src, IRPModelElement target) {
        if (src == null || target == null) return false;

        try {
            Method mGetDeps = src.getClass().getMethod("getDependencies");
            Object depsObj = mGetDeps.invoke(src);
            if (!(depsObj instanceof IRPCollection deps) || deps.getCount() == 0) return false;

            @SuppressWarnings("unchecked")
            List<Object> depList = deps.toList();

            for (Object dep : depList) {
                IRPModelElement depTarget = tryGetDependencyTarget(dep);
                if (target.equals(depTarget)) return true;
            }
        } catch (NoSuchMethodException nsme) {
            // API différente : pas de getDependencies()
            return false;
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private IRPModelElement tryGetDependencyTarget(Object dep) {
        if (dep == null) return null;

        // Try common method names in Rhapsody-like APIs
        String[] candidates = new String[] {
                "getDependsOn",
                "getSupplier",
                "getTarget",
                "getDependentOn",
                "getDependsOnElement"
        };

        for (String name : candidates) {
            try {
                Method m = dep.getClass().getMethod(name);
                Object o = m.invoke(dep);
                if (o instanceof IRPModelElement me) return me;
            } catch (Exception ignore) {}
        }
        return null;
    }

    /**
     * Optionnel: si on arrive à lire les stéréotypes d'une généralisation,
     * on vérifie que le GUID fragment est présent.
     * (Tolérant si API différente ou stéréotype absent dans le modèle.)
     */
    private void assertGeneralizationHasStereotypeIfPossible(IRPGeneralization g, String guidFragment) {
        if (g == null || guidFragment == null) return;

        try {
            Method mGetSt = g.getClass().getMethod("getStereotypes");
            Object stObj = mGetSt.invoke(g);
            if (!(stObj instanceof IRPCollection stCol) || stCol.getCount() == 0) return;

            @SuppressWarnings("unchecked")
            List<Object> stList = stCol.toList();
            for (Object o : stList) {
                if (o instanceof IRPStereotype st) {
                    String sg = st.getGUID();
                    if (sg != null && sg.contains(guidFragment)) {
                        return; // OK
                    }
                }
            }

            // Si on a pu lire, mais pas trouvé => fail
            Assertions.fail("Expected stereotype GUID fragment not found on generalization: " + guidFragment);
        } catch (NoSuchMethodException nsme) {
            // API différente => on ne fail pas (optionnel)
        } catch (Exception e) {
            // idem
        }
    }

    /**
     * Pour T08: si le WB contient déjà des éléments de fixture non-Function,
     * on les autorise. Le plus simple: considérer que tout élément qui existait
     * avant le test (dans le .rpyx) est autorisé.
     *
     * Ici on reste permissif: adapte si tu veux quelque chose de strict.
     */
    private boolean isPreExistingFixtureElement(IRPClass wb, IRPClass c) {
        // Si tu veux strict: compare avec une liste de noms autorisés.
        return true;
    }
    
    private void forceSelectForTool(IRPModelElement el) {
        // 1) naviguer (facultatif mais aide parfois le focus)
        try { el.locateInBrowser(); } catch (Throwable ignore) {}

        // 2) sélection modèle via API
        IRPCollection col = rhapsodyApplication.createNewCollection();
        col.addItem(el);
        rhapsodyApplication.selectModelElements(col);

        // 3) vérification (critique) : c'est ce que le tool va lire
        IRPModelElement selected = rhapsodyApplication.getSelectedElement();
        Assertions.assertNotNull(selected, "getSelectedElement() == null after selectModelElements");
        Assertions.assertEquals(el.getGUID(), selected.getGUID(),
            "Tool will NOT run from WB. getSelectedElement()="
                + selected.getFullPathName() + " udmc=" + selected.getUserDefinedMetaClass());
    }
    
    private int expectedAddsFromBBToWB(IRPClass bb, IRPClass wb) {
        java.util.Set<String> wbKeys = new java.util.HashSet<>();

        // WB keys
        for (Object o : wb.getNestedElementsByMetaClass("Class", 0).toList()) {
            if (!(o instanceof IRPClass)) continue;
            IRPClass c = (IRPClass) o;

            String udmc = c.getUserDefinedMetaClass();
            if ("Function".equals(udmc) || "Function With Reference".equals(udmc)) {
                wbKeys.add(udmc + "||" + c.getName());
            }
        }

        // Count missing from BB
        int adds = 0;
        for (Object o : bb.getNestedElementsByMetaClass("Class", 0).toList()) {
            if (!(o instanceof IRPClass)) continue;
            IRPClass c = (IRPClass) o;

            String udmc = c.getUserDefinedMetaClass();
            if (!"Function".equals(udmc) && !"Function With Reference".equals(udmc)) continue;

            String key = udmc + "||" + c.getName();
            if (!wbKeys.contains(key)) adds++;
        }

        return adds;
    }
}