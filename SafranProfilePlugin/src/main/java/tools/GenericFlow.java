package tools;

import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import tools.strategies.GenericFlowClassPort;
import tools.strategies.GenericFlowClasses;
import tools.strategies.GenericFlowPortClass;
import tools.strategies.GenericFlowStrategy;

/**
 * <b>Implementation of specific action</b>
 * <p>
 * This class with do something...
 * Description of the admin helper
 * </p>
 * 
 * This class implements the Strategy Pattern to apply different strategies
 * for different types of GenericFlow.
 * 
 * @author s570589
 *
 */
public class GenericFlow extends RhapsodyTool {

    public static final String COMMAND = "Safran Toolkit...\\Generic Flow Update";
    
    /**
     * Defines the strategy of GenericFlow update
     */
    private GenericFlowStrategy strategy;

    /**
     * Constructor
     * @param rpyApp
     */
    public GenericFlow(IRPApplication rpyApp) {
        super(rpyApp);
    }

    /**
     * Manages multiple selection
     */
    @Override
    public void execute() {

        List<?> selectedGraphElementList = rhApp.getSelectedGraphElements().toList();

        for (Object selected : selectedGraphElementList) {

            if (selected instanceof IRPGraphElement) {
                IRPGraphElement selectedGraphElement = (IRPGraphElement) selected;
                
                IRPModelElement correspondingModelElement = selectedGraphElement.getModelObject();

                if (correspondingModelElement instanceof IRPFlow) {
                    
                    IRPModelElement source = ((IRPFlow) correspondingModelElement).getEnd1();
                    IRPModelElement target = ((IRPFlow) correspondingModelElement).getEnd2();

                    // Conditionally set the strategy to use:
                    if (source instanceof IRPClass && target instanceof IRPClass) {
                        // Strategy for a flow between 2 Classes
                        strategy = new GenericFlowClasses(rhApp);
                    } else if (source instanceof IRPSysMLPort && target instanceof IRPClass) {
                        // Strategy for a flow between 1 Port and 1 Class
                        strategy = new GenericFlowPortClass(rhApp, (IRPSysMLPort) source, (IRPClass) target);
                    } else if (source instanceof IRPClass && target instanceof IRPSysMLPort) {
                        // Strategy for a flow between 1 Class and 1 Port
                        strategy = new GenericFlowClassPort(rhApp, (IRPClass) source, (IRPSysMLPort) target);
                    } else {
                        rhpLog.error("Generic Flow does not use valid ports or classes.");
                        continue;
                    }

                    // Apply the chosen strategy
                    if (strategy != null) {
                        strategy.apply(selectedGraphElement);
                    }
                } else {
                    rhpLog.error("Cannot apply " + COMMAND + " to " + correspondingModelElement.getFullPathName());
                }
            }
        }
    }

    @Override
    public String commandName() {
        return COMMAND;
    }
    
	@Override
	public boolean isUndoable() {
		return true;
	}
}
