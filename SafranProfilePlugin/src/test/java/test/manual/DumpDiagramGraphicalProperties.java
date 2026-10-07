package test.manual;

import java.util.List;

import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphEdge;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPGraphNode;
import com.telelogic.rhapsody.core.IRPGraphicalProperty;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

/**
 * Programme de diagnostic, en LECTURE SEULE : il ne modifie rien dans le modele.
 * <p>
 * Il se connecte au Rhapsody deja ouvert, recupere le diagramme de l'element
 * selectionne, puis affiche dans la console :
 * <ul>
 *   <li>pour chaque bloc : son nom, Position, Width, Height ;</li>
 *   <li>pour chaque lien : sa source, sa cible et TOUTES ses proprietes
 *       graphiques (nom = valeur).</li>
 * </ul>
 * Objectif : connaitre les noms exacts des proprietes d'un lien (extremites,
 * points intermediaires...) avant d'ecrire le repositionnement des liens
 * de "Rearrange Tree Layout".
 * </p>
 *
 * <p><b>Utilisation :</b></p>
 * <ol>
 *   <li>Ouvrir Rhapsody et le projet, ouvrir un diagramme d'arbre (FBS, LBS...).</li>
 *   <li>Cliquer sur un bloc DANS le diagramme (pas dans le browser).</li>
 *   <li>Dans Eclipse : clic droit sur cette classe, Run As, Java Application.</li>
 * </ol>
 * <p>Pour limiter la sortie, on peut passer en argument le nombre maximum de
 * liens detailles (par defaut 3).</p>
 */
public class DumpDiagramGraphicalProperties {

	/** Nombre de liens dont on affiche toutes les proprietes, par defaut. */
	private static final int DEFAULT_MAX_DETAILED_EDGES = 3;

	public static void main(String[] args) {
		// Nombre de liens detailles : argument optionnel du programme
		int maxDetailedEdges = DEFAULT_MAX_DETAILED_EDGES;
		if (args.length > 0) {
			maxDetailedEdges = Integer.parseInt(args[0].trim());
		}

		// 1) Connexion au Rhapsody deja lance (ne cree pas de nouvelle instance)
		IRPApplication app = RhapsodyAppServer.getActiveRhapsodyApplication();
		if (app == null) {
			System.out.println("Aucun Rhapsody ouvert : lancez Rhapsody puis reessayez.");
			return;
		}

		// 2) Diagramme contenant l'element selectionne
		IRPDiagram diagram = app.getDiagramOfSelectedElement();
		if (diagram == null) {
			System.out.println("Aucun diagramme trouve : selectionnez un bloc DANS un diagramme.");
			return;
		}
		System.out.println("Diagramme : " + diagram.getName()
				+ " (metaclasse : " + diagram.getMetaClass() + ")");

		@SuppressWarnings("unchecked")
		List<IRPGraphElement> elements = diagram.getGraphicalElements().toList();

		// 3) Les blocs : on n'affiche que la geometrie, utile pour les ancrages
		System.out.println();
		System.out.println("===== BLOCS =====");
		for (IRPGraphElement ge : elements) {
			if (ge instanceof IRPGraphNode) {
				System.out.println(describe(ge)
						+ " | Position=" + valueOf(ge, "Position")
						+ " | Width=" + valueOf(ge, "Width")
						+ " | Height=" + valueOf(ge, "Height"));
			}
		}

		// 4) Les liens : source, cible, puis toutes les proprietes graphiques
		System.out.println();
		System.out.println("===== LIENS =====");
		int edgeIndex = 0;
		for (IRPGraphElement ge : elements) {
			if (!(ge instanceof IRPGraphEdge)) continue;
			IRPGraphEdge edge = (IRPGraphEdge) ge;
			edgeIndex++;

			System.out.println();
			System.out.println("--- Lien " + edgeIndex + " : " + describe(edge));
			System.out.println("    source = " + describe(edge.getSource()));
			System.out.println("    cible  = " + describe(edge.getTarget()));

			// Au-dela de la limite, on ne detaille plus pour garder une sortie lisible
			if (edgeIndex > maxDetailedEdges) {
				System.out.println("    (proprietes non detaillees, limite = " + maxDetailedEdges + ")");
				continue;
			}

			// getAllGraphicalProperties() : methode recommandee par la Javadoc
			// pour connaitre les proprietes disponibles sur un element graphique
			@SuppressWarnings("unchecked")
			List<IRPGraphicalProperty> props = edge.getAllGraphicalProperties().toList();
			for (IRPGraphicalProperty p : props) {
				System.out.println("    " + p.getKey() + " = " + p.getValue());
			}
		}

		System.out.println();
		System.out.println("Termine : " + edgeIndex + " lien(s) trouve(s).");
	}

	/**
	 * Texte court identifiant un element graphique : nom et metaclasse
	 * de l'element de modele qu'il represente.
	 */
	private static String describe(IRPGraphElement ge) {
		if (ge == null) return "<null>";
		IRPModelElement mo = ge.getModelObject();
		if (mo == null) return "<sans element de modele>";
		String udm = mo.getUserDefinedMetaClass();
		return mo.getName() + " [" + (udm != null && !udm.isEmpty() ? udm : mo.getMetaClass()) + "]";
	}

	/**
	 * Valeur d'une propriete graphique, ou "?" si elle n'existe pas
	 * pour ce type d'element (l'API leve alors une exception).
	 */
	private static String valueOf(IRPGraphElement ge, String name) {
		try {
			IRPGraphicalProperty p = ge.getGraphicalProperty(name);
			return p == null ? "?" : p.getValue();
		} catch (Exception e) {
			return "?";
		}
	}
}
