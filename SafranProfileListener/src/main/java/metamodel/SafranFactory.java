package metamodel;

import java.util.HashMap;
import java.util.Map;

import com.telelogic.rhapsody.core.IRPModelElement;

import logging.RhapsodyLogger;

public class SafranFactory {

	/**
	 * Key: Rhapsody GUID
	 * Value: ModelElement reference
	 */
	private final Map<String, ModelElement> stub = new HashMap<>();

	private static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();

	private static final String HANDLER_PACKAGE = "metamodel.";

	public SafranFactory() {

	}

	public ModelElement get(IRPModelElement pModelElement) {
		return stub.get(pModelElement.getGUID());
	}

	public ModelElement create(IRPModelElement modelElement) {

	    String guid = modelElement.getGUID();

	    if (stub.containsKey(guid)) {
	        return stub.get(guid);
	    }

	    rhpLog.debug("Creating a new instance for "+modelElement.getFullPathName());

	    String stereotypeName = null;
	    if (modelElement.getNewTermStereotype() != null) {
	        stereotypeName = modelElement.getNewTermStereotype().getName();
	    }

	    // fallback if no new-term stereotype exists
	    if (stereotypeName == null || stereotypeName.isEmpty()) {
	        // Use meta-class (Class, Object, Package, etc.)
	        stereotypeName = modelElement.getMetaClass();
	    }

	    String className = HANDLER_PACKAGE + stereotypeName;

	    try {
	        Class<?> clazz = Class.forName(className);

	        ModelElement createdInstance = (ModelElement) clazz
	                .getDeclaredConstructor(IRPModelElement.class)
	                .newInstance(modelElement);

	        return createdInstance;

	    } catch (ClassNotFoundException e) {

	        rhpLog.debug("No specific handler for type '" + stereotypeName
	                + "', using default ModelElement");

	        return new ModelElement(modelElement);

	    } catch (Exception e) {

	        rhpLog.error("Handler instantiation failed for type '" + stereotypeName
	                + "': " + e.getMessage());

	        return new ModelElement(modelElement);
	    }
	}
}
