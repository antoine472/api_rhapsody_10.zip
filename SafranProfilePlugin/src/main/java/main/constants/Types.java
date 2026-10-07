package main.constants;

public class Types {

	/**
	 * recursive Use 1 to specify that the retrieval should be recursive.
	 * Use 0 if you only want to retrieve the relevant elements from the first level below the current element.
	 * @author s570589
	 *
	 */
	public static class Recursive {
		public static final int NOT_RECURSIVE = 0;
	    public static final int RECURSIVE = 1;
	    
	}
	
	public static class Direction {
		public static final String IN_OUT = "InOut";
		public static final String IN = "In";
		public static final String OUT = "Out";
		
	}


	public static class Unit {
		// Use 1 to specify that the element should be saved in its own file.
		public static final int STORE_SEPARETED_FILE = 1;
		// Use 0 to specify that the element should not be saved in its own file
		public static final int STORE_IN_MODEL = 0;
		
		// 1 if the unit is not currently loaded, 0 if it is currently loaded 
		public static final int UNLOADED = 1;
		
		// 1 if the file is read-only
		public static final int READ_ONLY = 1;
		// 0 if the file is not read-only
		public static final int NOT_READ_ONLY = 0;
	}
	
	/**
	 * val Use 1 to specify that the plugin should be notified when an element is modified.
	 * Use 0 to specify that the plugin should not be notified when elements are modified.
	 */
	public static class Notifications {
		public static final int ACTIVE = 1;
		public static final int INACTIVE = 0;
	}
}
