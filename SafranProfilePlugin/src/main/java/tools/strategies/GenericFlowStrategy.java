package tools.strategies;

import com.telelogic.rhapsody.core.IRPGraphElement;

public interface GenericFlowStrategy {
	
	public boolean isTypedPortsWithFlowItem = true;
	
	public void apply(IRPGraphElement selectedGraphElement);

}
