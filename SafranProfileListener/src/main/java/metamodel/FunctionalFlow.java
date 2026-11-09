package metamodel;

import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;

import logging.RhapsodyLogger;

public class FunctionalFlow extends Flow {

	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	/**
	 * Constructor needs to be IRPModelElement due to Factory constructor
	 */
	public FunctionalFlow(IRPModelElement itemFlow) {
		super((IRPFlow) itemFlow);
		//rhpLog.info("Created Functional Flow Item");
		System.out.println("Created Functional Flow Item");
	}

	@Override
	protected boolean needsDiagramReowner() {
		return true;
	}
}
