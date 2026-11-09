package metamodel;


import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;

import logging.RhapsodyLogger;

public class OperationalFlow extends Flow {

	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();


	/**
	 * Constructor needs to be IRPModelElement due to Factory constructor
	 * @param itemFlow
	 */
	public OperationalFlow(IRPModelElement itemFlow) {
		super((IRPFlow) itemFlow);
		//rhpLog.info("Created Operational Flow Item");
		System.out.println("Created Operational Flow Item");
	}

	@Override
	protected boolean needsDiagramReowner() {
		return true;
	}
}
