package metamodel;

import com.telelogic.rhapsody.core.IRPModelElement;

import logging.RhapsodyLogger;

public class ModelElement {

	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	protected IRPModelElement rhpModelElement;

	public ModelElement(IRPModelElement modelElement) {
		rhpModelElement = modelElement;
	}

	public void onDoubleClick() {

	}

	public void afterAdded() {

	}


}
