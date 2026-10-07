package tools;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPDiagram;

import tools.RhapsodyTool;

public class ReturnHome extends RhapsodyTool {

    public static final String COMMAND = "Return home";
    
    private static final String HOME_PAGE_GUID = "GUID 21770c96-9bc3-4fe9-b489-f18e40cfd25e";
    
    private IRPDiagram homePage = null;

    public ReturnHome(IRPApplication rpyApp) {
        super(rpyApp);
        homePage = (IRPDiagram) rhApp.activeProject().findElementByGUID(HOME_PAGE_GUID);
        
        if(homePage == null) {
        	rhpLog.error("Rhapsody Home Page is not present. Return home feature is disabled.");
        }   
    }

    @Override
    public String commandName() {
        return COMMAND;
    }

    @Override
    public void execute() {
    	if(homePage == null) {
    		rhpLog.error("Rhapsody Home Page is not loaded. Return home is disabled.");
    		return;
    	}
    	
    	rhpLog.debug("Open Rhapsody Home Page Requested.");
    	homePage.openDiagram();
    }
    
	@Override
	public boolean isUndoable() {
		return false;
	}

}
