package main;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RPUserPlugin;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

import logging.RhapsodyLogger;
import main.constants.Direction;
import main.gui.tools.Toast;
import tools.AddLifecyclePhase;
import tools.CleanRedefinedPortsInDiagram;
import tools.Config;
import tools.ConfigurePropagateProperties;
import tools.DefineDirection;
import tools.DuplicateServiceFunction;
import tools.ExportSystem;
import tools.FlowItemStereotypePropagation;
import tools.GenerateFBS;
import tools.GenerateLBS;
import tools.GenerateTBD;
import tools.GenericFlow;
import tools.LocateFlowItemInBrowser;
import tools.OpenParentDiagram;
import tools.PropagatePortProperties;
import tools.ReturnHome;
import tools.RearrangeTreeLayout;
import tools.ReverseFlowDirection;
import tools.RhapsodyTool;
import tools.SelectFlowItemDefinition;
import tools.SelectFunctionDefinition;
import tools.SelectModeDefinition;
import tools.SendToDefinition;
import tools.SetReferenceDefinition;
import tools.UpdateFunctionDefinition;
import tools.UpdateFunctionAllocation;
import tools.UpdateLogicalRealization;
import tools.UpdateLogicalSystemReuseDefinition;
import tools.UpdateRedefinedPorts;


public class SafranProfilePlugin extends RPUserPlugin {

	private static final String BUILD_VERSION = "20261007_19-10";

	private static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	HashMap<String, RhapsodyTool> tools = new HashMap<>();
	private static final ThreadLocal<Integer> UNDO_DEPTH = ThreadLocal.withInitial(() -> 0);

	IRPApplication rhpApp;
	IRPProject rhpPrj;

	/**
	 * Simple example of execution
	 *
	 * Use this method to debug the feature.
	 * @param args
	 */
	public static void main(String[] args) {
		IRPApplication app = RhapsodyAppServer.getActiveRhapsodyApplication();
		rhpLog.initialize(app);

		rhpLog.info("Test execution of plugin");

		SafranProfilePlugin safranTest = new SafranProfilePlugin();
		safranTest.RhpPluginInit(app);

		rhpLog.info("Simulate an execution of user command");
		safranTest.OnMenuItemSelect(FlowItemStereotypePropagation.COMMAND);
		rhpLog.info("Simulation end execution");
	}

	@Override
	public void RhpPluginInit(IRPApplication rpyApplication) {
		rhpApp = rpyApplication;

		rhpLog.initialize(rhpApp);

		rhpLog.info("Connected to Rhapsody");
		rhpLog.debug("Build version used: " + BUILD_VERSION);

		// add all tools
		tools.put(Config.COMMAND, new Config(rhpApp));
		tools.put(UpdateFunctionAllocation.COMMAND, new UpdateFunctionAllocation(rhpApp));
		tools.put(UpdateLogicalRealization.COMMAND, new UpdateLogicalRealization(rhpApp));
		tools.put(ExportSystem.COMMAND, new ExportSystem(rhpApp));
		tools.put(SendToDefinition.COMMAND, new SendToDefinition(rhpApp));
		tools.put(GenericFlow.COMMAND, new GenericFlow(rhpApp));
		tools.put(UpdateFunctionDefinition.COMMAND, new UpdateFunctionDefinition(rhpApp));
		tools.put(AddLifecyclePhase.COMMAND, new AddLifecyclePhase(rhpApp));
		tools.put(UpdateLogicalSystemReuseDefinition.COMMAND, new UpdateLogicalSystemReuseDefinition(rhpApp));
		tools.put(SelectFlowItemDefinition.COMMAND, new SelectFlowItemDefinition(rhpApp));
		tools.put(SelectFunctionDefinition.COMMAND, new SelectFunctionDefinition(rhpApp));
		tools.put(SelectModeDefinition.COMMAND, new SelectModeDefinition(rhpApp));
		tools.put(ReturnHome.COMMAND, new ReturnHome(rhpApp));
		tools.put(OpenParentDiagram.COMMAND, new OpenParentDiagram(rhpApp));
		tools.put(PropagatePortProperties.COMMAND, new PropagatePortProperties(rhpApp));
		tools.put(ConfigurePropagateProperties.COMMAND, new ConfigurePropagateProperties(rhpApp));
		tools.put(GenerateFBS.COMMAND, new GenerateFBS(rhpApp));
		tools.put(GenerateLBS.COMMAND, new GenerateLBS(rhpApp));
		tools.put(GenerateTBD.COMMAND, new GenerateTBD(rhpApp));
		tools.put(FlowItemStereotypePropagation.COMMAND, new FlowItemStereotypePropagation(rhpApp));
		tools.put(DefineDirection.COMMAND_INPUT, new DefineDirection(rhpApp, Direction.In));
		tools.put(DefineDirection.COMMAND_OUTPUT, new DefineDirection(rhpApp, Direction.Out));
		tools.put(DefineDirection.COMMAND_BIDIRECTIONAL, new DefineDirection(rhpApp, Direction.InOut));
		tools.put(DuplicateServiceFunction.COMMAND, new DuplicateServiceFunction(rhpApp));
		tools.put(ReverseFlowDirection.COMMAND, new ReverseFlowDirection(rhpApp));
		tools.put(RearrangeTreeLayout.COMMAND, new RearrangeTreeLayout(rhpApp));
		tools.put(UpdateRedefinedPorts.COMMAND, new UpdateRedefinedPorts(rhpApp));
		tools.put(LocateFlowItemInBrowser.COMMAND, new LocateFlowItemInBrowser(rhpApp));
		tools.put(CleanRedefinedPortsInDiagram.COMMAND, new CleanRedefinedPortsInDiagram(rhpApp));
		tools.put(SetReferenceDefinition.COMMAND, new SetReferenceDefinition(rhpApp));
	}

	@Override
	public void RhpPluginInvokeItem() {
	}

	@Override
	public void OnMenuItemSelect(String menuItem) {

		RhapsodyTool commandTool = tools.get(menuItem);

		if (commandTool == null) {
			rhpLog.error("No existing tool for: " + menuItem);
			Toast.showToast("No existing tool for: " + menuItem, 5000);
			return;
		}

		rhpLog.debug("Start command: " + menuItem);

		final int previousDepth = UNDO_DEPTH.get();
		final boolean outermost = (previousDepth == 0);
		UNDO_DEPTH.set(previousDepth + 1);

		boolean refreshFrozen   = false;
		boolean projectTxStarted = false;
		boolean undoTxStarted   = false;

		IRPProject project = null;

		try {
			utils.UndoTrace.mark(rhpApp, rhpLog, "ENTER OnMenuItemSelect (outermost=" + outermost + ")");

			// Non-undoable tools run outside any plugin transaction. The selector tools
			// (Select ... definition, Set Reference Definition) are among them: their
			// command only opens a non-modal selector and returns; each OK / Apply /
			// in-dialog edit then runs on the selector worker in its own undo
			// transaction (see main.gui.tools.SelectorWorker), so Rhapsody draws it live.
			if (!commandTool.isUndoable()) {
				utils.UndoTrace.mark(rhpApp, rhpLog, "BEFORE execute() (non-undoable)");
				commandTool.execute();
				utils.UndoTrace.mark(rhpApp, rhpLog, "AFTER execute() (non-undoable)");
				return;
			}

			if (outermost) {
				// PERF-1 FIX: skip browser/GE refresh freeze for interactive (dialog-based) tools.
				// Previously the freeze was held for the entire execute() duration, which includes
				// the blocking modal dialog. When the dialog closes, all deferred redraws flush
				// simultaneously — causing the multi-second delay the user experiences after
				// clicking "Select". Non-interactive tools keep the freeze as before.
				if (!commandTool.isInteractive()) {
					try {
						utils.UndoTrace.mark(rhpApp, rhpLog, "BEFORE freeze refresh");
						rhpApp.allowBrowserRefresh(0);
						rhpApp.allowGERefresh(0);
						refreshFrozen = true;
						utils.UndoTrace.mark(rhpApp, rhpLog, "AFTER freeze refresh");
					} catch (Exception e) {
						rhpLog.error("Cannot freeze refresh: " + e);
						utils.UndoTrace.mark(rhpApp, rhpLog, "freeze refresh EXCEPTION");
					}
				}

				try {
					project = rhpApp.activeProject();
					utils.UndoTrace.mark(rhpApp, rhpLog, "BEFORE project.startTransactionOfNoCGInterest()");
					project.startTransactionOfNoCGInterest();
					projectTxStarted = true;
					utils.UndoTrace.mark(rhpApp, rhpLog, "AFTER project.startTransactionOfNoCGInterest()");
				} catch (Exception e) {
					rhpLog.error("startTransactionOfNoCGInterest failed: " + e);
					utils.UndoTrace.mark(rhpApp, rhpLog, "project tx START EXCEPTION");
				}

				try {
					utils.UndoTrace.mark(rhpApp, rhpLog, "BEFORE startUndoTransaction()");
					rhpApp.startUndoTransaction();

					String startErr = "";
					try { startErr = rhpApp.errorMessage(); } catch (Exception ignore) {}

					if (startErr != null && !startErr.isBlank()) {
						rhpLog.error("startUndoTransaction errorMessage: " + startErr);
						undoTxStarted = false;
						utils.UndoTrace.mark(rhpApp, rhpLog, "AFTER startUndoTransaction (ERROR)");
					} else {
						undoTxStarted = true;
						utils.UndoTrace.mark(rhpApp, rhpLog, "AFTER startUndoTransaction (OK)");
					}

				} catch (Exception e) {
					rhpLog.error("startUndoTransaction failed: " + e);
					undoTxStarted = false;
					utils.UndoTrace.mark(rhpApp, rhpLog, "startUndoTransaction EXCEPTION");
				}
			}

			utils.UndoTrace.mark(rhpApp, rhpLog, "BEFORE commandTool.execute()");
			commandTool.execute();
			utils.UndoTrace.mark(rhpApp, rhpLog, "AFTER commandTool.execute()");

		} catch (Exception e) {

			rhpLog.error("Exception during command: " + menuItem + " -> " + e);

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			rhpLog.error(sw.toString());

			utils.UndoTrace.mark(rhpApp, rhpLog, "commandTool.execute() EXCEPTION");

		} finally {

			UNDO_DEPTH.set(previousDepth);

			if (outermost) {
				utils.UndoTrace.mark(rhpApp, rhpLog, "FINALLY (outermost) BEFORE endUndoTransaction()");

				if (undoTxStarted) {
					try {
						rhpApp.endUndoTransaction();

						String endErr = "";
						try { endErr = rhpApp.errorMessage(); } catch (Exception ignore) {}
						if (endErr != null && !endErr.isBlank()) {
							rhpLog.error("endUndoTransaction errorMessage: " + endErr);
						}

						utils.UndoTrace.mark(rhpApp, rhpLog, "FINALLY (outermost) AFTER endUndoTransaction()");
					} catch (Exception endEx) {
						rhpLog.error("endUndoTransaction failed: " + endEx);
						utils.UndoTrace.mark(rhpApp, rhpLog, "endUndoTransaction EXCEPTION");
					}
				} else {
					utils.UndoTrace.mark(rhpApp, rhpLog, "FINALLY (outermost) SKIP endUndoTransaction (not started)");
				}

				if (projectTxStarted && project != null) {
					try {
						utils.UndoTrace.mark(rhpApp, rhpLog, "FINALLY (outermost) BEFORE endTransactionOfNoCGInterest()");
						project.endTransactionOfNoCGInterest();
						utils.UndoTrace.mark(rhpApp, rhpLog, "FINALLY (outermost) AFTER endTransactionOfNoCGInterest()");
					} catch (Exception e) {
						rhpLog.error("endTransactionOfNoCGInterest failed: " + e);
						utils.UndoTrace.mark(rhpApp, rhpLog, "endTransactionOfNoCGInterest EXCEPTION");
					}
				}

				if (refreshFrozen) {
					try {
						utils.UndoTrace.mark(rhpApp, rhpLog, "FINALLY (outermost) BEFORE unfreeze refresh");
						rhpApp.allowGERefresh(1);
						rhpApp.allowBrowserRefresh(1);
						utils.UndoTrace.mark(rhpApp, rhpLog, "FINALLY (outermost) AFTER unfreeze refresh");
					} catch (Exception e) {
						rhpLog.error("Cannot unfreeze refresh: " + e);
						utils.UndoTrace.mark(rhpApp, rhpLog, "unfreeze refresh EXCEPTION");
					}
				}

				utils.UndoTrace.mark(rhpApp, rhpLog, "EXIT OnMenuItemSelect (outermost)");
			}

			try {
				rhpLog.debug("After command, canUndo() = " + rhpApp.canUndo());
			} catch (Exception ignore) {
				// no-op
			}
		}
	}

	@Override
	public void OnTrigger(String trigger) {
	}

	@Override
	public boolean RhpPluginCleanup() {
		return false;
	}

	@Override
	public void RhpPluginFinalCleanup() {
	}

}
