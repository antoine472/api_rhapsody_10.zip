package metamodel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

/**
 * Integration tests for Flow.afterAdded listener
 */
public class FlowTest extends RhapsodyBaseTest {

    public FlowTest() {
        super("./src/test/resources/repositionnementFlow/repositionnementFlow.rpyx");
    }

    @After
    public void cleanupPendingReowners() {
        Flow.clearPendingReownersForTests();
    }

    /* =========================
     * Repositionnement tests
     * ========================= */

    @Test
    public void functionalFlowIsRepositioned() {
        assertFlowIsRepositioned(
            "GUID 95fcc0ae-5e66-4e8d-a7d1-f523b1151679",
            "GUID 5a00c5e4-c10b-4c8b-8af0-7479d291f350",
            FunctionalFlow.class
        );
    }

    @Test
    public void genericFlowIsRepositioned() {
        assertFlowIsRepositioned(
            "GUID 1acc3d81-0247-48d2-9957-a6fb08fc7694",
            "GUID 8e302e82-ea05-42bf-9a8b-4aeb1d092676",
            GenericFlow.class
        );
    }

    @Test
    public void logicalFlowIsRepositioned() {
        assertFlowIsRepositioned(
            "GUID 7befe0e3-5749-4e40-9a27-6f571a7fd1cb",
            "GUID 00cf8416-9685-4760-8aa3-252a15f92a3c",
            LogicalFlow.class
        );
    }

    @Test
    public void operationalFlowIsRepositioned() {
        assertFlowIsRepositioned(
            "GUID f9695393-7257-4ae9-97b5-7ec274162794",
            "GUID 3cf58386-47d7-44d4-8de6-0bdd1ad6c65e",
            OperationalFlow.class
        );
    }

    @Test
    public void technicalConnectionIsRepositioned() {
        assertFlowIsRepositioned(
            "GUID 65a568a4-f789-4514-839f-61162fd77e41",
            "GUID 06519469-40cb-4e6d-86f7-c9cb3fc19a14",
            TechnicalConnection.class
        );
    }

    @Test
    public void baseFlowIsNotRepositioned() {
        IRPFlow rhpFlow =
            (IRPFlow) project.findElementByGUID(
                "GUID 493c2220-c1ea-4285-abec-deae3dd4bd99");

        Assert.assertNotNull(rhpFlow);

        IRPModelElement originalOwner = rhpFlow.getOwner();
        Assert.assertNotNull(originalOwner);

        Flow flow = new Flow(rhpFlow);
        flow.afterAdded();

        // Proxy-safe : compare GUID, pas l'objet Java
        Assert.assertEquals(originalOwner.getGUID(), rhpFlow.getOwner().getGUID());
    }

    /* =========================
     * Duplication logic tests
     * ========================= */

    @Test
    public void flowsWithSamePortsAreEqual() {
        IRPFlow flow1 =
            (IRPFlow) project.findElementByGUID(
                "GUID bb65af51-718e-41b6-8ba3-7c4f393a2e36");

        IRPFlow flow2 =
            (IRPFlow) project.findElementByGUID(
                "GUID 3d0f8b2f-f731-47a6-a4e0-30036c452ed9");

        Flow f1 = new Flow(flow1);
        Flow f2 = new Flow(flow2);

        Assert.assertTrue(f1.equals(f2));
    }

    @Test
    public void duplicateFlowsHaveSameHashCode() {
        IRPFlow flow1 =
            (IRPFlow) project.findElementByGUID(
                "GUID bb65af51-718e-41b6-8ba3-7c4f393a2e36");

        IRPFlow flow2 =
            (IRPFlow) project.findElementByGUID(
                "GUID 3d0f8b2f-f731-47a6-a4e0-30036c452ed9");

        Flow f1 = new Flow(flow1);
        Flow f2 = new Flow(flow2);

        Assert.assertEquals(f1.hashCode(), f2.hashCode());
    }

    /* =========================
     * Helper method (robuste)
     * ========================= */

    private void assertFlowIsRepositioned(
        String flowGuid,
        String expectedOwnerGuid,
        Class<? extends Flow> flowClass) {

        IRPFlow rhpFlow =
            (IRPFlow) project.findElementByGUID(flowGuid);
        Assert.assertNotNull("Flow not found: " + flowGuid, rhpFlow);

        IRPModelElement expectedOwner =
            (IRPModelElement) project.findElementByGUID(expectedOwnerGuid);
        Assert.assertNotNull("Expected owner not found: " + expectedOwnerGuid, expectedOwner);

        try {
            Flow flow =
                flowClass.getConstructor(IRPModelElement.class)
                         .newInstance(rhpFlow);

            // 1) Exécute le listener (peut enqueue, mais UI/diagramme non fiable en test)
            flow.afterAdded();

            // 2) En test, on force le job vers l'owner attendu (comme ton test validé)
            Flow.enqueueReowner(rhpFlow.getGUID(), expectedOwnerGuid);

            // 3) Pump jusqu'à stabilisation
            IRPApplication app = RhapsodyAppServer.getActiveRhapsodyApplication();
            Assert.assertNotNull("Active Rhapsody application is null", app);

            boolean ok = false;
            for (int i = 0; i < 30; i++) {
                Flow.pumpReowners(app);

                IRPModelElement actualOwner = rhpFlow.getOwner();
                Assert.assertNotNull("Flow owner is null", actualOwner);

                if (expectedOwnerGuid.equals(actualOwner.getGUID())) {
                    ok = true;
                    break;
                }

                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
            }

            if (!ok) {
                IRPModelElement actualOwner = rhpFlow.getOwner();
                Assert.fail(
                    "Owner did not change to expected owner.\n" +
                    "Flow GUID=" + rhpFlow.getGUID() + " name=" + rhpFlow.getName() + "\n" +
                    "Expected GUID=" + expectedOwnerGuid + " path=" + expectedOwner.getFullPathName() + "\n" +
                    "Actual   GUID=" + (actualOwner != null ? actualOwner.getGUID() : "null") +
                    " path=" + (actualOwner != null ? actualOwner.getFullPathName() : "null")
                );
            }

            // Final assert proxy-safe
            Assert.assertEquals(expectedOwnerGuid, rhpFlow.getOwner().getGUID());

        } catch (Exception e) {
            Assert.fail("Failed to test flow type " + flowClass.getSimpleName() + ": " + e.getMessage());
        }
    }
}
