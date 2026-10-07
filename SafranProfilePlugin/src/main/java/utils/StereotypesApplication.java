package utils;

import java.util.List;

import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPStereotype;

public class StereotypesApplication {
	
	public static void setStereotypes(IRPModelElement source, IRPModelElement target, boolean includeNewTerms) {
		
		List<IRPStereotype> sourceStereotypes = source.getStereotypes().toList();
		
		for (IRPStereotype stereotype : sourceStereotypes) {
			System.out.println("apply "+stereotype.getName());
			if(stereotype.getIsNewTerm() == 0) {
				try {
					target.addSpecificStereotype(stereotype);
				} catch (Exception e) {
					System.out.println("Cannot apply stereotype "+stereotype.getName()+" on "+target.getName());
				}
				
			}
			if(stereotype.getIsNewTerm() == 1 && includeNewTerms) {
				target.addSpecificStereotype(stereotype);
			}
		}
	}

}
