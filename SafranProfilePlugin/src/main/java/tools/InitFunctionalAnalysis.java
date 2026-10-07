package tools;

import com.telelogic.rhapsody.core.IRPApplication;


/**
 * <b>Extract a Logical System to library</b>
 * <p>
 * This feature extracts all informations inside a Logical System to be exported to library of a Rhapsody Model.
 * </p>
 * @author s570589
 *
 */
public class InitFunctionalAnalysis extends RhapsodyTool {
	
	public static final String COMMAND = "Safran Toolkit...\\Initialize Functional Analysis";

		
	public InitFunctionalAnalysis(IRPApplication rpyApp) {
		super(rpyApp);
	}

	/*
	 * All logic is implemented here 
	 */
	@Override
	public void execute() {
		

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
