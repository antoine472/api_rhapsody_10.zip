package test;

import java.io.File;

import org.junit.After;
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

    /** Path to the model for this test case */
    protected String pathToModel;
    
    protected IRPProject project;

    /**
     * Constructor called for every test case. Initializes the path to the test model.
     */
    public RhapsodyBaseTest(String pathToTestModel) {    
        this.pathToModel = pathToTestModel;
    }

    /**
     * Called once before any tests are executed.
     * Creates a Rhapsody Application instance.
     */
    @BeforeClass
    public static void initializeRhapsody() {
        rhapsodyApplication = RhapsodyAppServer.createRhapsodyApplication();
        
        rhapsodyApplication.setHiddenUI(false);

        Assert.assertNotNull("Failed to create Rhapsody Application.", rhapsodyApplication);
    }

    /**
     * Called once after all tests are executed.
     * Quits the Rhapsody Application.
     */
    @AfterClass
    public static void shutdownRhapsody() {
        if (rhapsodyApplication != null) {
            try {
            	rhapsodyApplication.activeProject().close();
                rhapsodyApplication.quit();
            } catch (RhapsodyRuntimeException e) {
                System.out.println("Error while quitting Rhapsody Application: " + e.getMessage());
            }
        }
    }

    /**
     * Executed before each test method.
     * Loads the project specified by the path.
     */
    @Before
    public void setUp() {
    	try {
    		loadProject();
		} catch (Exception e) {
			System.out.println("Error while loading Rhapsody Project: " + e.getMessage());
		}
        
    }

    /**
     * Executed after each test method.
     * Closes the active project.
     */
    @After
    public void tearDown() {
    }

    /**
     * Loads the project specified by the path.
     */
    private void loadProject() {
        // Validate the path is not empty
        Assert.assertNotNull("Model path is null.", pathToModel);
        Assert.assertFalse("Model path is empty.", pathToModel.isEmpty());

        // Ensure the path has a valid .rpyx extension
        if (!pathToModel.endsWith(".rpyx")) {
            Assert.fail("Invalid model file extension. Expected '.rpyx'.");
        }

        // Check if the file exists
        File modelFile = new File(pathToModel);
        
        Assert.assertTrue("Model file does not exist at the specified path: " + pathToModel, modelFile.exists());

        // Open the project in Rhapsody
        project = rhapsodyApplication.openProject(modelFile.getAbsolutePath());
        Assert.assertNotNull("Failed to open the project. No active project found.", project);
    }
    
    protected void selectModelElementInRhapsody(IRPModelElement selectedModelElement) {
		// select Port in rhapsody
		IRPCollection collection = rhapsodyApplication.createNewCollection();
		collection.addItem(selectedModelElement);
		rhapsodyApplication.selectModelElements(collection);
    }
    
    protected void selectGraphElementInRhapsody(IRPGraphElement selectedgraphElement) {
		// select Port in rhapsody
		IRPCollection collection = rhapsodyApplication.createNewCollection();
		collection.addGraphicalItem(selectedgraphElement);
		rhapsodyApplication.selectGraphElements(collection);
    }
}
