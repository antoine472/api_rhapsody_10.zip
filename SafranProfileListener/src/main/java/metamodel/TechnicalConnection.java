package metamodel;


import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;

import logging.RhapsodyLogger;

public class TechnicalConnection extends Flow {

	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();
	/**
	 * Constructor needs to be IRPModelElement due to Factory constructor
	 * @param itemFlow
	 */
	public TechnicalConnection(IRPModelElement itemFlow) {
		super((IRPFlow) itemFlow);
		//rhpLog.info("Created Operational Flow Item");
		System.out.println("Created Technical Flow Item");
	}

	@Override
	protected boolean needsDiagramReowner() {
		return true;
	}
}
