package metamodel;


import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;

import logging.RhapsodyLogger;

public class LogicalFlow extends Flow {

	/**
	 * Constructor needs to be IRPModelElement due to Factory constructor
	 * @param itemFlow
	 */
	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	public LogicalFlow(IRPModelElement itemFlow) {
		super((IRPFlow) itemFlow);
		//rhpLog.info("Created Logical Flow Item");
		System.out.println("Created Logical Flow Item");
	}

	@Override
	protected boolean needsDiagramReowner() {
		return true;
	}
}
