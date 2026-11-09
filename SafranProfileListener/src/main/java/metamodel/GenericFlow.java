package metamodel;

import com.telelogic.rhapsody.core.IRPFlow;
import com.telelogic.rhapsody.core.IRPModelElement;

import logging.RhapsodyLogger;

public class GenericFlow extends Flow {


	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	/**
	 * Constructor needs to be IRPModelElement due to Factory constructor
	 */
	public GenericFlow(IRPModelElement itemFlow) {
		super((IRPFlow) itemFlow);
		System.out.println("Created Generic Flow Item");
	}

	@Override
	protected boolean needsDiagramReowner() {
		return true;
	}
}
