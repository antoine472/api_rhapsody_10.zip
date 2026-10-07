package test;

import java.io.File;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

public abstract class RhapsodyBaseTest {

    /** Reference to the current Rhapsody Application */
    protected static IRPApplication rhapsodyApplication;

    /** Path to the model for this test case */
    protected final String pathToModel;

    protected IRPProject project;

    protected RhapsodyBaseTest(String pathToTestModel) {
        this.pathToModel = pathToTestModel;
    }

    @BeforeAll
    public static void initializeRhapsody() {
        rhapsodyApplication = RhapsodyAppServer.createRhapsodyApplication();
        Assertions.assertNotNull(rhapsodyApplication, "Failed to create Rhapsody Application.");
        rhapsodyApplication.setHiddenUI(false);
    }

    @AfterAll
    public static void shutdownRhapsody() {
        try {
            if (rhapsodyApplication == null) return;

            var p = rhapsodyApplication.activeProject();
            if (p != null) {
                try { p.close(); } catch (Throwable ignore) {}
            }
            try { rhapsodyApplication.quit(); } catch (Throwable ignore) {}
        } catch (Throwable t) {
            System.err.println("shutdownRhapsody failed: " + t.getMessage());
        } finally {
            rhapsodyApplication = null;
        }
    }

    @BeforeEach
    public void setUp() {
        loadProject();
    }

    @AfterEach
    public void tearDown() {
        try {
            if (rhapsodyApplication != null && rhapsodyApplication.activeProject() != null) {
                rhapsodyApplication.activeProject().close();
            }
        } catch (Exception e) {
            System.out.println("Error while closing Rhapsody Project: " + e.getMessage());
        } finally {
            project = null;
        }
    }

    private void loadProject() {
        Assertions.assertNotNull(pathToModel, "Model path is null.");
        Assertions.assertFalse(pathToModel.isEmpty(), "Model path is empty.");
        Assertions.assertTrue(pathToModel.endsWith(".rpyx"), "Invalid model file extension. Expected '.rpyx'.");

        File modelFile = new File(pathToModel);
        Assertions.assertTrue(modelFile.exists(),
                "Model file does not exist at the specified path: " + pathToModel);

        project = rhapsodyApplication.openProject(modelFile.getAbsolutePath());
        Assertions.assertNotNull(project, "Failed to open the project. No active project found.");
    }

    protected void selectModelElementInRhapsody(IRPModelElement selectedModelElement) {
        IRPCollection collection = rhapsodyApplication.createNewCollection();
        collection.addItem(selectedModelElement);
        rhapsodyApplication.selectModelElements(collection);
    }

    protected void selectGraphElementInRhapsody(IRPGraphElement selectedGraphElement) {
        IRPCollection collection = rhapsodyApplication.createNewCollection();
        collection.addGraphicalItem(selectedGraphElement);
        rhapsodyApplication.selectGraphElements(collection);
    }
}