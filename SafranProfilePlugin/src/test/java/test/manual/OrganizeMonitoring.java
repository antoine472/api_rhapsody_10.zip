package test.manual;

import java.util.ArrayList;
import java.util.List;

import com.telelogic.rhapsody.core.HYPNameType;
import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPCollection;
import com.telelogic.rhapsody.core.IRPDiagram;
import com.telelogic.rhapsody.core.IRPGraphElement;
import com.telelogic.rhapsody.core.IRPHyperLink;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;
import com.telelogic.rhapsody.core.IRPTableLayout;
import com.telelogic.rhapsody.core.IRPTableView;
import com.telelogic.rhapsody.core.RhapsodyAppServer;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

import test.manual.MonitoringPlan.DiagramIndex;
import test.manual.MonitoringPlan.Kind;
import test.manual.MonitoringPlan.Move;
import test.manual.MonitoringPlan.Tile;

/**
 * Programme de test : range le package Monitoring du profil et aligne la page
 * Home sur l'ensemble des types de diagrammes du profil.
 *
 * <p><b>Pourquoi un programme et pas une modification du .sbsx</b> : chaque
 * element du profil porte un identifiant attribue par le serveur RMM
 * (_rmmServerID). Un element cree en editant le fichier n'en aurait pas ;
 * cree par l'API dans Rhapsody connecte, il en recoit un.</p>
 *
 * <p><b>Etapes</b> (le plan est dans MonitoringPlan) :</p>
 * <ol>
 *   <li>cree les dossiers OK/Diagram Indexes/A..D, OK/Analysis Tables Page,
 *       OK/Other Tables (NOK et TBD existent deja) ;</li>
 *   <li>deplace les vues de la racine de Monitoring dans leur dossier ; le
 *       layout d'une vue la suit s'il est aussi a la racine. Ce qui est deja
 *       dans OK, NOK ou TBD n'est pas touche ;</li>
 *   <li>cree les 9 tables d'index manquantes (copie de la vue et du layout
 *       "Logical Sequence diagrams", type d'element = New Term du diagramme),
 *       avec leur hyperlien ;</li>
 *   <li>fixe l'image des vignettes nouvelles et des 3 vignettes dont l'image
 *       etait un chemin absolu sur un poste ;</li>
 *   <li>place les vignettes de la page Home : une ligne centree par bande,
 *       meme taille dans une bande. La colonne de droite n'est pas touchee.</li>
 * </ol>
 *
 * <p><b>Idempotent</b> : un element deja range ou deja cree est saute, on peut
 * relancer le programme.</p>
 *
 * <p><b>Arguments</b> : DRY affiche ce qui serait fait sans rien modifier.</p>
 *
 * <p><b>Utilisation</b> : ouvrir le profil SafranArchitectureProfile dans
 * Rhapsody (connecte au serveur), puis Run As, Java Application (d'abord avec
 * DRY). Toutes les modifications sont dans UNE transaction : un seul Ctrl+Z
 * les annule. Enregistrer ensuite le profil dans Rhapsody.</p>
 */
public class OrganizeMonitoring {

	/** Proprietes d'affichage des vignettes (memes valeurs que les vignettes existantes). */
	private static final String[][] TILE_PROPERTIES = {
			{ "General.Graphics.EnableImageView", "True" },
			{ "General.Graphics.ImageOnlyShowName", "True" },
			{ "General.Graphics.ImageViewLayout", "ImageOnly" },
			{ "Format.HyperLink.Font.FontColor", "0,0,0" },
			{ "Format.HyperLink.Font.Weight", "700" } };

	private final IRPApplication app;
	private final boolean dryRun;
	private final IRPPackage monitoring;
	/** Compte rendu, affiche a la fin. */
	private final List<String> report = new ArrayList<>();

	OrganizeMonitoring(IRPApplication app, IRPPackage monitoring, boolean dryRun) {
		this.app = app;
		this.monitoring = monitoring;
		this.dryRun = dryRun;
	}

	public static void main(String[] args) {
		boolean dry = args.length > 0 && "DRY".equalsIgnoreCase(args[0]);
		IRPApplication app = RhapsodyAppServer.getActiveRhapsodyApplication();
		if (app == null) {
			System.out.println("Rhapsody n'est pas ouvert.");
			return;
		}
		IRPModelElement found = app.activeProject().findNestedElementRecursive("Monitoring", "Package");
		if (!(found instanceof IRPPackage)) {
			System.out.println("Package Monitoring introuvable : ouvrir le profil SafranArchitectureProfile.");
			return;
		}
		OrganizeMonitoring job = new OrganizeMonitoring(app, (IRPPackage) found, dry);
		System.out.println(dry ? "== DRY : aucune modification" : "== Modification du profil");
		if (!dry) app.startUndoTransaction();
		try {
			job.organize();
			job.createMissingIndexes();
			job.layoutHome();
		} finally {
			if (!dry) closeUndoTransaction(app);
		}
		job.report.forEach(System.out::println);
	}

	// ------------------------------------------------------------ 1 et 2 : rangement

	/** Cree les dossiers puis deplace les elements de la racine de Monitoring. */
	void organize() {
		for (Move m : MonitoringPlan.moves()) {
			IRPModelElement el = findChild(monitoring, m.name(), m.kind());
			if (el == null) {
				// absent de la racine : deja range (relance) ou supprime entre temps
				report.add("  deja range ou absent : " + m.name());
				continue;
			}
			IRPPackage target = folder(m.folder());
			report.add("  deplace " + m.kind() + " '" + m.name() + "' -> " + m.folder());
			if (dryRun) continue;
			// le layout d'une vue la suit s'il est a la racine (lu avant le deplacement)
			IRPTableLayout layout = el instanceof IRPTableView v ? v.getItsTableLayout() : null;
			el.setOwner(target);
			if (layout != null && isChildOfMonitoring(layout)) {
				layout.setOwner(target);
				report.add("      avec son layout '" + layout.getName() + "'");
			}
		}
	}

	/**
	 * Dossier designe par un chemin relatif a Monitoring ("OK/Diagram Indexes/A - Operational"),
	 * cree s'il manque (en DRY, rien n'est cree : la methode renvoie null).
	 */
	IRPPackage folder(String path) {
		IRPPackage current = monitoring;
		for (String part : path.split("/")) {
			IRPModelElement next = current.findNestedElement(part, "Package");
			if (next instanceof IRPPackage p) {
				current = p;
			} else {
				report.add("  cree le dossier " + path);
				if (dryRun) return null;
				current = current.addNestedPackage(part);
			}
		}
		return current;
	}

	// ------------------------------------------------------------ 3 et 4 : tables d'index

	/** Cree les tables d'index manquantes et corrige les images des vignettes. */
	void createMissingIndexes() {
		IRPTableView template = (IRPTableView) findRecursive(MonitoringPlan.TEMPLATE_VIEW, Kind.VIEW);
		for (DiagramIndex d : MonitoringPlan.DIAGRAM_INDEX_TABLES) {
			IRPTableView view = (IRPTableView) findRecursive(d.view(), Kind.VIEW);
			if (view == null && d.created()) {
				report.add("  cree la table '" + d.view() + "' (type " + d.elementType() + ") dans "
						+ MonitoringPlan.levelFolder(d.level()));
				if (dryRun || template == null) continue;
				view = createIndex(template, d);
			} else if (view == null) {
				report.add("  ATTENTION table introuvable : " + d.view());
				continue;
			}
			if (d.image() != null) {
				report.add("  image de '" + d.view() + "' : " + MonitoringPlan.IMAGE_DIR + d.image());
				if (!dryRun) hyperLinkOf(view).setPropertyValue("General.Graphics.AssociatedImageFile",
						MonitoringPlan.IMAGE_DIR + d.image());
			}
		}
		if (template == null) report.add("  ATTENTION vue modele introuvable : " + MonitoringPlan.TEMPLATE_VIEW);
	}

	/** Copie la vue modele et son layout, regle le type d'element et l'hyperlien. */
	private IRPTableView createIndex(IRPTableView template, DiagramIndex d) {
		IRPPackage target = folder(MonitoringPlan.levelFolder(d.level()));
		// layout : copie du modele, liste le New Term du diagramme
		IRPTableLayout layout = (IRPTableLayout) template.getItsTableLayout().clone(d.layoutName(), target);
		IRPCollection types = app.createNewCollection();
		types.setSize(1);
		types.setString(1, d.elementType()); // collections Rhapsody : indices a partir de 1
		layout.setElementTypes(types);
		// vue : copie du modele, pointee sur le nouveau layout
		IRPTableView view = (IRPTableView) template.clone(d.view(), target);
		view.setItsTableLayout(layout);
		// la copie emporte l'hyperlien du modele : il est redirige vers la nouvelle vue
		IRPHyperLink link = hyperLinkOf(view);
		if (link == null) link = (IRPHyperLink) view.addNewAggr("HyperLink", d.view());
		link.setTarget(view);
		link.setDisplayOption(HYPNameType.RP_HYP_NAMETEXT, "");
		for (String[] p : TILE_PROPERTIES) link.setPropertyValue(p[0], p[1]);
		return view;
	}

	// ------------------------------------------------------------ 5 : page Home

	/** Place les vignettes des tables d'index, bande par bande. */
	void layoutHome() {
		IRPDiagram home = (IRPDiagram) findChild(monitoring, "Home", Kind.DIAGRAM);
		if (home == null) {
			report.add("  ATTENTION diagramme Home introuvable");
			return;
		}
		for (int band = 0; band < MonitoringPlan.LEVELS.length; band++) {
			List<DiagramIndex> tables = MonitoringPlan.tablesOfBand(band);
			List<Tile> tiles = MonitoringPlan.layoutBand(band, tables.size());
			for (int i = 0; i < tables.size(); i++) {
				placeTile(home, tables.get(i).view(), tiles.get(i));
			}
		}
	}

	/** Pose (ou deplace) la vignette de l'hyperlien d'une table. */
	private void placeTile(IRPDiagram home, String viewName, Tile t) {
		String where = t.x() + "," + t.y() + " " + t.w() + "x" + t.h();
		IRPModelElement view = findRecursive(viewName, Kind.VIEW);
		IRPHyperLink link = view == null ? null : hyperLinkOf(view);
		if (link == null) {
			// en DRY, une table a creer n'existe pas encore
			report.add("  vignette '" + viewName + "' -> " + where + (dryRun ? " (apres creation)" : " IMPOSSIBLE : pas d'hyperlien"));
			return;
		}
		IRPCollection nodes = home.getCorrespondingGraphicElements(link);
		boolean exists = nodes != null && nodes.getCount() > 0;
		report.add("  vignette '" + viewName + "' " + (exists ? "deplacee" : "posee") + " -> " + where);
		if (dryRun) return;
		if (!exists) {
			home.addNewNodeForElement(link, t.x(), t.y(), t.w(), t.h());
			return;
		}
		IRPGraphElement ge = (IRPGraphElement) nodes.getItem(1);
		ge.setGraphicalProperty("Width", String.valueOf(t.w()));
		ge.setGraphicalProperty("Height", String.valueOf(t.h()));
		ge.setGraphicalProperty("Position", t.x() + "," + t.y());
	}

	// ------------------------------------------------------------ recherches

	/** Enfant direct d'un package, par nom et nature (une vue et un layout peuvent porter le meme nom). */
	static IRPModelElement findChild(IRPModelElement owner, String name, Kind kind) {
		for (Object o : owner.getNestedElements().toList()) {
			IRPModelElement e = (IRPModelElement) o;
			if (name.equals(e.getName()) && isKind(e, kind)) return e;
		}
		return null;
	}

	/** Element de Monitoring ou de ses dossiers, par nom et nature. */
	IRPModelElement findRecursive(String name, Kind kind) {
		IRPModelElement e = findChild(monitoring, name, kind);
		if (e != null) return e;
		for (Object o : monitoring.getNestedElements().toList()) {
			if (o instanceof IRPPackage p) {
				e = findInPackage(p, name, kind);
				if (e != null) return e;
			}
		}
		return null;
	}

	/** Element d'un package ou de ses sous-packages, par nom et nature (premier trouve). */
	static IRPModelElement findInPackage(IRPPackage p, String name, Kind kind) {
		IRPModelElement e = findChild(p, name, kind);
		if (e != null) return e;
		for (Object o : p.getNestedElements().toList()) {
			if (o instanceof IRPPackage sub) {
				e = findInPackage(sub, name, kind);
				if (e != null) return e;
			}
		}
		return null;
	}

	private static boolean isKind(IRPModelElement e, Kind kind) {
		return switch (kind) {
		case VIEW -> e instanceof IRPTableView;
		case LAYOUT -> e instanceof IRPTableLayout;
		case DIAGRAM -> e instanceof IRPDiagram;
		};
	}

	/** Premier hyperlien agrege par une vue de table (sa vignette), ou null. */
	static IRPHyperLink hyperLinkOf(IRPModelElement view) {
		for (Object o : view.getNestedElements().toList()) {
			if (o instanceof IRPHyperLink h) return h;
		}
		return null;
	}

	private boolean isChildOfMonitoring(IRPModelElement e) {
		// comparaison par GUID : deux objets Java peuvent designer le meme element Rhapsody
		IRPModelElement owner = e.getOwner();
		return owner != null && owner.getGUID().equals(monitoring.getGUID());
	}

	/**
	 * Ferme la transaction ; Rhapsody leve "Transaction was not created" si
	 * rien n'a ete modifie (meme cas que RepositionTreeLinks).
	 */
	private static void closeUndoTransaction(IRPApplication app) {
		try {
			app.endUndoTransaction();
		} catch (RhapsodyRuntimeException e) {
			System.out.println("(transaction : " + e.getMessage() + ")");
		}
	}
}
