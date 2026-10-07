package utils;

import java.util.List;
import java.util.stream.Collectors;

import com.telelogic.rhapsody.core.IRPClass;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPSysMLPort;

import logging.RhapsodyLogger;

public class PortCreation {
	
	protected static RhapsodyLogger rhpLog = RhapsodyLogger.getInstance();
	
	public static IRPSysMLPort createPort(IRPClass portOwner) {
		IRPSysMLPort createdPort = null;

		// Fetch existing port names and determine the next available index
		@SuppressWarnings("unchecked")
		List<IRPModelElement> ports = portOwner.getPorts().toList();
		List<String> existingPortNames = ports.stream()
				.map((IRPModelElement port) -> port.getName())
				.collect(Collectors.toList());

		int nextIndex = determineNextAvailableIndex(existingPortNames);
		
		createdPort = (IRPSysMLPort) portOwner.addNewAggr("Flow Port", "p_" + nextIndex);

		return createdPort;
	}
	
	// Determine the next available index based on existing port names that match the pattern "p_<number>"
	private static int determineNextAvailableIndex(List<String> portNames) {
		int maxIndex = 0;
		for (String name : portNames) {
			if (name.startsWith("p_")) {
				try {
					int index = Integer.parseInt(name.substring(2));
					if (index > maxIndex) {
						maxIndex = index;
					}
				} catch (NumberFormatException e) {
					// Ignore names that do not conform to the expected pattern "p_<number>"
				}
			}
		}
		return maxIndex + 1;
	}
}
