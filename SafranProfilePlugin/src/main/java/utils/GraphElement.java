package utils;

import java.util.List;

import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphicalProperty;

public class GraphElement {

	static public void getAllGraphicalProperties(IRPGraphElement graphElement) {

		List<IRPGraphicalProperty> graphicalPropeties = null;

		try { /// in case the diagram graphic has no corresponding model element

			System.out.println(graphElement.getModelObject().getMetaClass());
			System.out.println("==========");


			graphicalPropeties = graphElement.getAllGraphicalProperties().toList();

			for (IRPGraphicalProperty graphProp : graphicalPropeties) {

				System.out.println(graphProp.getKey() + "\t" + graphProp.getValue());
			}
		}
		catch (Exception e) {	// diagram graphic has no corresponding model element.
			// graphic element may contain a "Type" graphical property
			System.out.println("<Unknown> Model Element");
			System.out.println("=======================");

			IRPCollection gps = graphElement.getAllGraphicalProperties();
			for (int i=0; i < gps.getCount(); i++) {
				IRPGraphicalProperty gp = (IRPGraphicalProperty) gps.getItem(i);
				if (gp != null) // Null graphical properties may exist
					System.out.println(gp.getKey() + "\t" + gp.getValue());
			}

		}
	}
}

