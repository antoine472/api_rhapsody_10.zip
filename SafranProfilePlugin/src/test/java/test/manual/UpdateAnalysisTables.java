package test.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.telelogic.rhapsody.core.HYPNameType;
import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPHyperLink;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPPackage;
import com.telelogic.rhapsody.core.IRPTableLayout;
import com.telelogic.rhapsody.core.IRPTableView;
import com.telelogic.rhapsody.core.RhapsodyAppServer;
import com.telelogic.rhapsody.core.RhapsodyRuntimeException;

import test.manual.AnalysisTablesPlan.Getter;
import test.manual.AnalysisTablesPlan.TableUpdate;
import test.manual.MonitoringPlan.Kind;

/**
 * Programme de test : met a jour les 12 tables posees en vignette sur la page
 * Monitoring::Analysis Tables, sur le modele de la table de reference Operational ICD
 * du profil SafranML v2 (plan et justification dans AnalysisTablesPlan).
 *
 * <p><b>Pour chaque table</b> :</p>
 * <ol>
 *   <li>Logical ICD et Technical ICD : copie du layout de reference (memes colonnes, memes
 *       getters), nouveau motif de contexte, labels de contexte des colonnes ; l'ancien
 *       layout, remplace, est range dans NOK ;</li>
 *   <li>renommage du layout et de la vue avec le suffixe _SafranMLv2 ;</li>
 *   <li>motif de contexte, noms de colonnes et getters du plan ; un getter n'est remplace
 *       que si la colonne porte bien le nom attendu ;</li>
 *   <li>vignette : texte libre egal au texte actuel (le renommage de la vue ne le change pas).
 *       La vignette Logical ICD, qui ouvrait une table de l'ancien profil, ouvre la nouvelle.</li>
 * </ol>
 * <p>Puis la sous-requete FunctionQuery (fonctions de la table Function covered by Use Case)
 * est renommee et son motif corrige.</p>
 *
 * <p><b>Ordre</b> : lancer d'abord OrganizeMonitoring (rangement), puis ce programme.
 * Idempotent : une table deja mise a jour est retrouvee par son nouveau nom.</p>
 *
 * <p><b>Arguments</b> : DRY affiche ce qui serait fait sans rien modifier.</p>
 *
 * <p><b>Utilisation</b> : profil SafranArchitectureProfile ouvert dans Rhapsody, Run As,
 * Java Application (d'abord avec DRY). Une seule transaction : un Ctrl+Z annule tout.
 * Enregistrer ensuite le profil.</p>
 */
public class UpdateAnalysisTables {

	/** Vue de l'ancien profil qui porte aujourd'hui la vignette Logical ICD. */
	static final String OLD_LOGICAL_ICD_TILE_VIEW = "LogicalICDView";

	private final IRPPackage monitoring;
	private final boolean dryRun;
	private final List<String> report = new ArrayList<>();

	UpdateAnalysisTables(IRPPackage monitoring, boolean dryRun) {
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
		UpdateAnalysisTables job = new UpdateAnalysisTables((IRPPackage) found, dry);
		System.out.println(dry ? "== DRY : aucune modification" : "== Modification du profil");
		if (!dry) app.startUndoTransaction();
		try {
			for (TableUpdate t : AnalysisTablesPlan.TABLES) {
				job.update(t);
			}
			job.updateFunctionQuery();
		} finally {
			if (!dry) closeUndoTransaction(app);
		}
		job.report.forEach(System.out::println);
	}

	/** Applique le plan a une table. */
	void update(TableUpdate t) {
		report.add("== " + t.label());
		IRPTableView view = (IRPTableView) find(t.newView(), Kind.VIEW);
		if (view == null) view = (IRPTableView) find(t.oldView(), Kind.VIEW);
		if (view == null) {
			report.add("  ATTENTION vue introuvable : " + t.oldView());
			return;
		}
		IRPTableLayout layout = t.cloneOf() != null ? copiedLayout(t, view) : ownLayout(t, view);
		if (layout == null) return;
		rename(layout, t.newLayout());
		rename(view, t.newView());
		if (t.contextPattern() != null) {
			report.add("  motif : " + t.contextPattern());
			if (!dryRun) layout.setPropertyValue(AnalysisTablesPlan.CONTEXT_PATTERN, t.contextPattern());
		}
		for (Map.Entry<Integer, String> c : t.columnNames().entrySet()) {
			report.add("  colonne " + c.getKey() + " renommee '" + c.getValue() + "'");
			if (!dryRun) layout.setColumnName(c.getKey(), c.getValue());
		}
		for (Map.Entry<Integer, Getter> g : t.getters().entrySet()) {
			setGetter(layout, g.getKey(), g.getValue());
		}
		tile(t, view);
	}

	/** Layout actuel de la vue, s'il porte le nom attendu (ancien ou nouveau). */
	private IRPTableLayout ownLayout(TableUpdate t, IRPTableView view) {
		IRPTableLayout layout = view.getItsTableLayout();
		String name = layout == null ? null : layout.getName();
		if (name == null || !(name.equals(t.oldLayout()) || name.equals(t.newLayout()))) {
			report.add("  ATTENTION layout inattendu (" + name + ") : table non modifiee");
			return null;
		}
		return layout;
	}

	/**
	 * Layout copie de la reference : cree au premier passage (et l'ancien layout range
	 * dans NOK), retrouve par son nom ensuite. En DRY, rien n'est cree : null.
	 */
	private IRPTableLayout copiedLayout(TableUpdate t, IRPTableView view) {
		IRPTableLayout copy = (IRPTableLayout) find(t.newLayout(), Kind.LAYOUT);
		if (copy != null) return copy;
		String refName = AnalysisTablesPlan.byBase(t.cloneOf()).newLayout();
		report.add("  copie du layout " + refName + " -> " + t.newLayout());
		IRPTableLayout ref = (IRPTableLayout) find(refName, Kind.LAYOUT);
		if (dryRun) {
			report.add("  (la suite porte sur la copie, creee hors DRY)");
			return null;
		}
		if (ref == null) {
			report.add("  ATTENTION layout de reference introuvable : " + refName);
			return null;
		}
		copy = (IRPTableLayout) ref.clone(t.newLayout(), view.getOwner());
		view.setItsTableLayout(copy);
		// labels de contexte : ceux de la reference sont remplaces par ceux du nouveau motif
		for (int i = 0; i < copy.getColumnCount(); i++) {
			String label = t.contextLabels().get(copy.getColumnContext(i));
			if (label != null) copy.setColumnContext(i, label);
		}
		// l'ancien layout n'est plus utilise par la vue : range dans NOK
		IRPModelElement old = find(t.replacedLayout(), Kind.LAYOUT);
		IRPModelElement nok = monitoring.findNestedElement(MonitoringPlan.NOK, "Package");
		if (old != null && nok != null) {
			old.setOwner(nok);
			report.add("  ancien layout " + t.replacedLayout() + " range dans NOK");
		}
		return copy;
	}

	/** Remplace le getter d'une colonne apres verification de son nom. */
	private void setGetter(IRPTableLayout layout, int index, Getter g) {
		String header = layout.getColumnName(index);
		if (header == null || !header.trim().equalsIgnoreCase(g.header().trim())) {
			report.add("  ATTENTION colonne " + index + " = '" + header + "', attendu '" + g.header()
					+ "' : getter non modifie");
			return;
		}
		report.add("  getter colonne " + index + " '" + g.header() + "' (" + g.cellType() + ")");
		if (dryRun) return;
		layout.setColumnImplementationCellType(index, g.cellType());
		layout.setColumnImplementationImports(index, AnalysisTablesPlan.IMPORTS);
		layout.setColumnImplementationGetterCode(index, g.code());
	}

	/** Vignette de la table : texte libre fixe, et pour Logical ICD reprise de l'ancienne vignette. */
	private void tile(TableUpdate t, IRPTableView view) {
		IRPHyperLink link = OrganizeMonitoring.hyperLinkOf(view);
		if (link == null && "LogicalICD".equals(t.base())) {
			IRPModelElement oldView = find(OLD_LOGICAL_ICD_TILE_VIEW, Kind.VIEW);
			link = oldView == null ? null : OrganizeMonitoring.hyperLinkOf(oldView);
			if (link != null) {
				report.add("  vignette reprise de " + OLD_LOGICAL_ICD_TILE_VIEW + " (ancien profil)");
				if (!dryRun) {
					link.setOwner(view);
					link.setTarget(view);
				}
			}
		}
		if (link == null) {
			report.add("  ATTENTION pas de vignette pour cette table");
			return;
		}
		report.add("  vignette : texte '" + t.label() + "'");
		if (!dryRun) link.setDisplayOption(HYPNameType.RP_HYP_FREETEXT, t.label());
	}

	/** Sous-requete des fonctions : renommee, motif SafranML v2. */
	void updateFunctionQuery() {
		report.add("== Sous-requete " + AnalysisTablesPlan.FUNCTION_QUERY_OLD);
		String newName = AnalysisTablesPlan.FUNCTION_QUERY_OLD + AnalysisTablesPlan.LAYOUT_SUFFIX;
		IRPModelElement query = find(newName, Kind.LAYOUT);
		if (query == null) query = find(AnalysisTablesPlan.FUNCTION_QUERY_OLD, Kind.LAYOUT);
		if (query == null) {
			report.add("  ATTENTION sous-requete introuvable");
			return;
		}
		rename(query, newName);
		report.add("  motif : " + AnalysisTablesPlan.FUNCTION_QUERY_PATTERN);
		if (!dryRun) query.setPropertyValue(AnalysisTablesPlan.QUERY_CONTEXT_PATTERN,
				AnalysisTablesPlan.FUNCTION_QUERY_PATTERN);
	}

	private void rename(IRPModelElement e, String name) {
		if (name.equals(e.getName())) return;
		report.add("  renomme '" + e.getName() + "' -> '" + name + "'");
		if (!dryRun) e.setName(name);
	}

	/** Element de Monitoring ou de ses dossiers, par nom et nature. */
	private IRPModelElement find(String name, Kind kind) {
		return name == null ? null : OrganizeMonitoring.findInPackage(monitoring, name, kind);
	}

	/** Ferme la transaction ; Rhapsody leve une exception si rien n'a ete modifie. */
	private static void closeUndoTransaction(IRPApplication app) {
		try {
			app.endUndoTransaction();
		} catch (RhapsodyRuntimeException e) {
			System.out.println("(transaction : " + e.getMessage() + ")");
		}
	}
}
