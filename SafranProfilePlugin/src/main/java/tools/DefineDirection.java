package tools;

import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import main.constants.Direction;
import main.constants.ProfilConstants;

/**
 * Helper tool to define the direction of a SysML Flow Port (Input, Output, or Bidirectional).
 * <p>
 * If a selected port is typeless, this tool assigns a default "untyped" FlowItem.
 * Then it sets the port's direction based on the {@link Direction} specified.
 * </p>
 * 
 * <p><b>Usage:</b> Typically triggered via a context menu command defined in Rhapsody.</p>
 * 
 * @author s570589
 */
public class DefineDirection extends RhapsodyTool {

    public static final String COMMAND_INPUT = "Safran Toolkit...\\Define Direction...\\As input";
    public static final String COMMAND_OUTPUT = "Safran Toolkit...\\Define Direction...\\As output";
    public static final String COMMAND_BIDIRECTIONAL = "Safran Toolkit...\\Define Direction...\\As bidirectional";

    private Direction direction;
    private IRPClass untypedFlowItem;

    /**
     * Constructs the DefineDirection tool with a reference to the Rhapsody application.
     *
     * @param rpyApp the Rhapsody application instance
     */
    public DefineDirection(IRPApplication rpyApp) {
        super(rpyApp);
    }

    /**
     * Constructs the DefineDirection tool with a specified port direction.
     * Initializes the reference to the default "untyped" FlowItem.
     *
     * @param rhpApp    the Rhapsody application instance
     * @param direction the direction to assign to selected Flow Ports
     */
    public DefineDirection(IRPApplication rhpApp, Direction direction) {
        super(rhpApp);
        this.direction = direction;
        this.untypedFlowItem = (IRPClass) rhpApp.activeProject().findElementByGUID(ProfilConstants.UNTYPED_GUID);
    }

    /**
     * Executes the direction definition logic:
     * <ul>
     *     <li>Iterates over selected elements</li>
     *     <li>If the element is a typeless SysML Flow Port, assigns the default untyped FlowItem</li>
     *     <li>Sets the port direction to the specified {@link Direction}</li>
     * </ul>
     */
    @Override
    public void execute() {
        rhpLog.debug("Starting execution of direction definition.");

        @SuppressWarnings("unchecked")
        List<IRPModelElement> selectedElements = rhApp.getListOfSelectedElements().toList();

        for (IRPModelElement selectedElement : selectedElements) {
            rhpLog.debug("Processing: " + selectedElement.getFullPathName());

            if (selectedElement instanceof IRPSysMLPort) {
                IRPSysMLPort selectedPort = (IRPSysMLPort) selectedElement;

                if (selectedPort.isTypelessObject() == 1) {
                    selectedPort.setType(untypedFlowItem);
                    rhpLog.debug("Assigned default untyped FlowItem to typeless port.");
                }

                selectedPort.setPortDirection(direction.name());
                rhpLog.info("Set port '" + selectedPort.getDisplayName() + "' direction to: " + direction.name());
            }
        }
    }

    /**
     * Returns the default command name.
     * If dynamic command names are required based on direction, this should be overridden accordingly.
     *
     * @return a string representing the command name used for integration
     */
    @Override
    public String commandName() {
        switch (direction) {
            case In:
                return COMMAND_INPUT;
            case Out:
                return COMMAND_OUTPUT;
            case InOut:
                return COMMAND_BIDIRECTIONAL;
            default:
                return "Safran Toolkit...\\Define Direction...\\Undefined";
        }
    }

	@Override
	public boolean isUndoable() {
		return true;
	}
}
