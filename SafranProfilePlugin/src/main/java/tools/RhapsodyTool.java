package tools;

import com.telelogic.rhapsody.core.IRPApplication;

import logging.RhapsodyLogger;

public abstract class RhapsodyTool {

	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	protected IRPApplication rhApp;

	public RhapsodyTool(IRPApplication rpyApp) {
		rhApp = rpyApp;
		rhpLog.initialize(rhApp);
	}

	public abstract String commandName();

	public abstract void execute();

	/**
	 * Method that defines if the tools shall implement a Undo behavior
	 * @return boolean
	 */
	public abstract boolean isUndoable();

	/**
	 * Returns true if this tool opens a dialog during execute().
	 * SafranProfilePlugin uses this to skip freezing browser/GE refresh for the
	 * duration of the dialog: holding the freeze across a dialog causes a
	 * multi-second flush delay once it closes.
	 */
	public boolean isInteractive() {
		return false;
	}

	// ------------------------------------------------------------------
	// Live elements (selector tools: the model may change while a selector is open)
	// ------------------------------------------------------------------

	/** True for a Rhapsody GUID key ("GUID ..."); false for any other selector key. */
	protected static boolean isGuid(String key) {
		return key != null && key.startsWith("GUID");
	}

	/** GUID of {@code el}, or null when it cannot be read. */
	protected static String guidOf(com.telelogic.rhapsody.core.IRPModelElement el) {
		try {
			return el != null ? el.getGUID() : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Re-fetches the element of the active project with this GUID, so a tool
	 * working after its menu command returned never relies on a proxy that may
	 * be stale or deleted. Null when the key is not a GUID, the project is gone
	 * or the element no longer exists.
	 */
	protected com.telelogic.rhapsody.core.IRPModelElement findByGuid(String guid) {
		if (!isGuid(guid)) return null;
		try {
			com.telelogic.rhapsody.core.IRPProject project = rhApp.activeProject();
			return project != null ? project.findElementByGUID(guid) : null;
		} catch (Exception e) {
			return null;
		}
	}
}
