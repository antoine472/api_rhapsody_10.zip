package main;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.RPUserPlugin;

import logging.RhapsodyLogger;


public class SafranListenerPlugin extends RPUserPlugin {

	private static final String BUILD_VERSION="20251114_13-55";

	private static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	@Override
	public void RhpPluginInit(IRPApplication rpyApplication) {
		// Initialize Rhapsody logger
		rhpLog.initialize(rpyApplication);

		ApplicationListener safranListener = new ApplicationListener(rpyApplication);

		rhpLog.info("Connected Listener to Rhapsody");
		rhpLog.debug("Listener Build version used: "+BUILD_VERSION);
	}

	@Override
	public void RhpPluginInvokeItem() {

	}

	@Override
	public void OnMenuItemSelect(String menuItem) {

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
