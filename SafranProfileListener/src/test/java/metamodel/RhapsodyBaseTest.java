package metamodel;

import java.io.File;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RhapsodyAppServer;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

public class RhapsodyBaseTest {

    /** Reference to the current Rhapsody Application */
    protected static IRPApplication rhapsodyApplication;

    /** Shared opened project (cached across test methods) */
    protected static IRPProject sharedProject;

    /** Absolute path of the currently opened project */
    protected static String sharedProjectAbsPath;

    /** Path to the model for this test case */
    protected final String pathToModel;

    /** Project handle used by the current test instance (points to sharedProject) */
    protected IRPProject project;

    /**
     * Constructor called for every test case instance (JUnit creates 1 instance per @Test).
     */
    public RhapsodyBaseTest(String pathToTestModel) {
        this.pathToModel = pathToTestModel;
    }

    @BeforeClass
    public static void initializeRhapsody() {
        rhapsodyApplication = RhapsodyAppServer.createRhapsodyApplication();
        Assert.assertNotNull("Failed to create Rhapsody Application.", rhapsodyApplication);

        // Tip: for CI you may want true; for local debugging false
        rhapsodyApplication.setHiddenUI(false);
    }

    @AfterClass
    public static void shutdownRhapsody() {
        // Close the shared project once
        if (sharedProject != null) {
            try {
                sharedProject.close();
            } catch (RhapsodyRuntimeException e) {
                System.out.println("Error while closing shared project: " + e.getMessage());
            } finally {
                sharedProject = null;
                sharedProjectAbsPath = null;
            }
        }

        // Quit the application once
        if (rhapsodyApplication != null) {
            try {
                rhapsodyApplication.quit();
            } catch (RhapsodyRuntimeException e) {
                System.out.println("Error while quitting Rhapsody Application: " + e.getMessage());
            } finally {
                rhapsodyApplication = null;
            }
        }
    }

    /**
     * Executed before each test method.
     * Ensures the project is opened only once per model path and reused afterwards.
     */
    @Before
    public void setUp() {
        ensureProjectLoadedOnce();
        this.project = sharedProject;
        Assert.assertNotNull("Shared project is null after ensureProjectLoadedOnce().", this.project);
    }

    /**
     * Opens the project only if not already opened (or if a different model path is requested).
     */
    private void ensureProjectLoadedOnce() {
        Assert.assertNotNull("Model path is null.", pathToModel);
        Assert.assertFalse("Model path is empty.", pathToModel.isEmpty());

        if (!pathToModel.endsWith(".rpyx")) {
            Assert.fail("Invalid model file extension. Expected '.rpyx'.");
        }

        File modelFile = new File(pathToModel);
        Assert.assertTrue("Model file does not exist at: " + pathToModel, modelFile.exists());

        String absPath = modelFile.getAbsolutePath();

        // If already opened on the same model -> reuse
        if (sharedProject != null && absPath.equals(sharedProjectAbsPath)) {
            return;
        }

        // If another project is already open, close it once
        if (sharedProject != null) {
            try {
                sharedProject.close();
            } catch (RhapsodyRuntimeException e) {
                System.out.println("Error while closing previous shared project: " + e.getMessage());
            } finally {
                sharedProject = null;
                sharedProjectAbsPath = null;
            }
        }

        // Open the project once and cache it
        sharedProject = rhapsodyApplication.openProject(absPath);
        Assert.assertNotNull("Failed to open the project: " + absPath, sharedProject);

        sharedProjectAbsPath = absPath;
    }

    // ------------------------------------------------------------
    // UI selection helpers (unchanged)
    // ------------------------------------------------------------

    protected void selectModelElementInRhapsody(IRPModelElement selectedModelElement) {
        IRPCollection collection = rhapsodyApplication.createNewCollection();
        collection.addItem(selectedModelElement);
        rhapsodyApplication.selectModelElements(collection);
    }

    protected void selectGraphElementInRhapsody(IRPGraphElement selectedgraphElement) {
        IRPCollection collection = rhapsodyApplication.createNewCollection();
        collection.addGraphicalItem(selectedgraphElement);
        rhapsodyApplication.selectGraphElements(collection);
    }
}
