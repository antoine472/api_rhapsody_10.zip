package test.manual;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan de mise a jour des tables posees en vignette sur la page Monitoring::Analysis Tables,
 * alignees sur la table de reference OperationalICD (ex OperationalICDNewProfile) du profil
 * SafranML v2. Donnees pures (aucune dependance a Rhapsody) : le programme
 * UpdateAnalysisTables les applique, AnalysisTablesPlanTest les verifie.
 *
 * <p><b>Regles communes</b> :</p>
 * <ul>
 *   <li>noms : suffixe _SafranMLv2_Layout et _SafranMLv2_View ;</li>
 *   <li>termes du profil v2 uniquement dans les motifs de contexte ;</li>
 *   <li>getters : test instanceof avant tout cast, controle des null, pas de doublon ;</li>
 *   <li>les trois ICD (Operational, Logical, Technical) ont les memes colonnes et les memes
 *       getters : Logical et Technical sont des copies du layout Operational, partant
 *       respectivement du Logical Architecture Diagram et du Technical Architecture Diagram.</li>
 * </ul>
 */
public final class AnalysisTablesPlan {

	private AnalysisTablesPlan() {
	}

	/** Suffixes des noms SafranML v2. */
	public static final String LAYOUT_SUFFIX = "_SafranMLv2_Layout";
	public static final String VIEW_SUFFIX = "_SafranMLv2_View";

	/** Proprietes Rhapsody du motif de contexte (table) et du motif d'une sous-requete. */
	public static final String CONTEXT_PATTERN = "Model.TableLayout.ContextPattern";
	public static final String QUERY_CONTEXT_PATTERN = "Model.TableLayout.QueryContextPattern";

	/** Imports donnes a chaque getter (liste separee par des virgules, format de l'API). */
	public static final String IMPORTS = "com.telelogic.rhapsody.core.*,java.util.*";

	/** Types de cellule d'une colonne User Defined Method (constantes ImplementationCellType). */
	public static final String STRING = "String";
	public static final String MODEL_ELEMENT = "Model element";
	public static final String LIST = "List of model elements";

	/**
	 * Getter d'une colonne.
	 *
	 * @param header   nom actuel de la colonne, verifie avant modification
	 * @param cellType type de cellule
	 * @param code     corps Java du getter (variable d'entree : cellElement)
	 */
	public record Getter(String header, String cellType, String code) {
	}

	/**
	 * Mise a jour d'une table de la page Analysis Tables.
	 *
	 * @param label          texte de la vignette (inchange)
	 * @param oldView        nom actuel de la vue
	 * @param oldLayout      nom actuel du layout (null si le layout est remplace par une copie)
	 * @param base           racine du nouveau nom ("UseCase" donne UseCase_SafranMLv2_Layout / _View)
	 * @param cloneOf        racine de la table dont le layout est copie (null : layout modifie sur place)
	 * @param replacedLayout layout actuel remplace par la copie, range dans NOK (null si sans objet)
	 * @param contextPattern nouveau motif de contexte (null : inchange)
	 * @param columnNames    nouveaux noms de colonnes, par indice
	 * @param getters        nouveaux getters, par indice
	 * @param contextLabels  pour une copie : labels de contexte des colonnes a remplacer
	 *                       (label de la reference vers label du nouveau motif)
	 */
	public record TableUpdate(String label, String oldView, String oldLayout, String base, String cloneOf,
			String replacedLayout, String contextPattern, Map<Integer, String> columnNames,
			Map<Integer, Getter> getters, Map<String, String> contextLabels) {

		public String newLayout() {
			return base + LAYOUT_SUFFIX;
		}

		public String newView() {
			return base + VIEW_SUFFIX;
		}
	}

	// ------------------------------------------------------------------ getters

	/** Corps commun "diagrammes de New Term X qui referencent l'element de la ligne". */
	private static String diagramsReferencing(String instanceType, String newTerm) {
		return """
				// Diagrammes %2$s ou figure l'element de la ligne (sans doublon)
				if (!(cellElement instanceof %1$s)) {
				    return null;
				}
				ArrayList<IRPModelElement> diagrams = new ArrayList<IRPModelElement>();
				for (Object o : cellElement.getReferences().toList()) {
				    if (o instanceof IRPDiagram) {
				        IRPStereotype st = ((IRPDiagram) o).getNewTermStereotype();
				        if (st != null && "%2$s".equals(st.getName()) && !diagrams.contains(o)) {
				            diagrams.add((IRPDiagram) o);
				        }
				    }
				}
				return diagrams;
				""".formatted(instanceType, newTerm);
	}

	/** Direction du port d'une extremite (1 = A, 2 = B) : seul un port SysML en a une. */
	private static String icdDirection(int end) {
		return """
				// Direction du port %2$s (extremite end%1$d) du flux de la ligne.
				// Port SysML (Flow Port) : sa direction ; port standard (Technical Port) : pas de direction.
				if (!(cellElement instanceof IRPFlow)) {
				    return null;
				}
				IRPSysMLPort port = ((IRPFlow) cellElement).getEnd%1$dSysMLPort();
				return port != null ? port.getPortDirection() : null;
				""".formatted(end, end == 1 ? "A" : "B");
	}

	/** Type du port d'une extremite : getType() d'un port SysML, getOtherClass() d'un port standard. */
	private static String icdType(int end) {
		return """
				// Type du port %2$s (extremite end%1$d) du flux de la ligne
				if (!(cellElement instanceof IRPFlow)) {
				    return null;
				}
				IRPFlow flow = (IRPFlow) cellElement;
				IRPSysMLPort sysmlPort = flow.getEnd%1$dSysMLPort();
				if (sysmlPort != null) {
				    return sysmlPort.getType();
				}
				// Port standard (Technical Port) : son type est la classe de l'instance
				IRPPort port = flow.getEnd%1$dPort();
				return port != null ? port.getOtherClass() : null;
				""".formatted(end, end == 1 ? "A" : "B");
	}

	/** Stereotypes ajoutes (hors New Term) du port d'une extremite, SysML ou standard. */
	private static String icdStereotypes(int end) {
		return """
				// Stereotypes ajoutes (hors New Term) du port %2$s (extremite end%1$d) du flux
				if (!(cellElement instanceof IRPFlow)) {
				    return null;
				}
				IRPFlow flow = (IRPFlow) cellElement;
				IRPModelElement port = flow.getEnd%1$dSysMLPort();
				if (port == null) {
				    port = flow.getEnd%1$dPort();
				}
				if (port == null) {
				    return null;
				}
				ArrayList<IRPModelElement> stereotypeList = new ArrayList<IRPModelElement>();
				for (Object o : port.getStereotypes().toList()) {
				    // Le New Term du port (Flow Port, Technical Port) est deja visible : on ne garde que les autres
				    if (o instanceof IRPStereotype && ((IRPStereotype) o).getIsNewTerm() == 0) {
				        stereotypeList.add((IRPStereotype) o);
				    }
				}
				return stereotypeList;
				""".formatted(end, end == 1 ? "A" : "B");
	}

	/** Elements vehicules par le flux (inchange par rapport a la reference). */
	private static final String ICD_CONVEYED = """
			// Elements vehicules (conveyed) par le flux de la ligne
			if (!(cellElement instanceof IRPFlow)) {
			    return null;
			}
			IRPCollection conveyed = ((IRPFlow) cellElement).getConveyed();
			if (conveyed == null) {
			    return null;
			}
			// toList() renvoie une liste brute : conversion vers la liste attendue par la colonne
			@SuppressWarnings("unchecked")
			List<IRPModelElement> conveyedList = (List<IRPModelElement>) conveyed.toList();
			return conveyedList;
			""";

	/** Getters des colonnes 4 a 10 des trois ICD (memes colonnes que la reference). */
	private static Map<Integer, Getter> icdGetters() {
		Map<Integer, Getter> g = new LinkedHashMap<>();
		g.put(4, new Getter("Port A direction", STRING, icdDirection(1)));
		g.put(5, new Getter("Port A type", MODEL_ELEMENT, icdType(1)));
		g.put(6, new Getter("Port A stereotype", LIST, icdStereotypes(1)));
		g.put(7, new Getter("Conveyed flow", LIST, ICD_CONVEYED));
		g.put(8, new Getter("Port B stereotype", LIST, icdStereotypes(2)));
		g.put(9, new Getter("Port B type", MODEL_ELEMENT, icdType(2)));
		g.put(10, new Getter("Port B direction", STRING, icdDirection(2)));
		return g;
	}

	/** Use Case : phases de cycle de vie des Use_Case Diagrams qui le montrent. */
	private static final String UC_LIFECYCLE_PHASE = """
			// Phases de cycle de vie qui possedent un Use_Case Diagram ou figure le cas d'utilisation
			if (!(cellElement instanceof IRPUseCase)) {
			    return null;
			}
			ArrayList<IRPModelElement> phases = new ArrayList<IRPModelElement>();
			for (Object o : cellElement.getReferences().toList()) {
			    if (!(o instanceof IRPDiagram)) {
			        continue;
			    }
			    IRPDiagram diagram = (IRPDiagram) o;
			    IRPStereotype st = diagram.getNewTermStereotype();
			    if (st == null || !"Use_CaseDiagram".equals(st.getName())) {
			        continue;
			    }
			    IRPModelElement owner = diagram.getOwner();
			    IRPStereotype ownerSt = owner != null ? owner.getNewTermStereotype() : null;
			    if (ownerSt != null && "LifecyclePhase".equals(ownerSt.getName()) && !phases.contains(owner)) {
			        phases.add(owner);
			    }
			}
			return phases;
			""";

	/** Use Case : systemes operationnels et parties prenantes associes. */
	private static final String UC_OPERATIONAL_SYSTEMS = """
			// Operational Systems et Stakeholders relies au cas d'utilisation par une association
			if (!(cellElement instanceof IRPUseCase)) {
			    return null;
			}
			ArrayList<IRPModelElement> systems = new ArrayList<IRPModelElement>();
			for (Object o : cellElement.getReferences().toList()) {
			    if (!(o instanceof IRPRelation)) {
			        continue;
			    }
			    IRPRelation relation = (IRPRelation) o;
			    if (!"Association".equals(relation.getRelationType())) {
			        continue;
			    }
			    // Classe qui porte l'extremite d'association (l'acteur du cas d'utilisation)
			    IRPClassifier system = relation.getOfClass();
			    IRPStereotype st = system != null ? system.getNewTermStereotype() : null;
			    if (st != null && ("OperationalSystem".equals(st.getName()) || "Stakeholder".equals(st.getName()))
			            && !systems.contains(system)) {
			        systems.add(system);
			    }
			}
			return systems;
			""";

	/** Use Case : diagrammes de sequence qu'il contient. */
	private static final String UC_SEQUENCE_DIAGRAMS = """
			// Diagrammes de sequence contenus dans le cas d'utilisation (recursif)
			if (!(cellElement instanceof IRPUseCase)) {
			    return null;
			}
			ArrayList<IRPModelElement> diagrams = new ArrayList<IRPModelElement>();
			for (Object o : cellElement.getNestedElementsByMetaClass("SequenceDiagram", 1).toList()) {
			    if (o instanceof IRPSequenceDiagram && !diagrams.contains(o)) {
			        diagrams.add((IRPSequenceDiagram) o);
			    }
			}
			return diagrams;
			""";

	/** Use Case : fonctions qui en dependent (Satisfies). */
	private static final String UC_FUNCTIONS = """
			// Fonctions qui dependent du cas d'utilisation (dependance Satisfies d'une Function)
			if (!(cellElement instanceof IRPUseCase)) {
			    return null;
			}
			ArrayList<IRPModelElement> functions = new ArrayList<IRPModelElement>();
			for (Object o : cellElement.getReferences().toList()) {
			    if (!(o instanceof IRPDependency)) {
			        continue;
			    }
			    IRPModelElement source = ((IRPDependency) o).getDependent();
			    if (source instanceof IRPClass) {
			        IRPStereotype st = source.getNewTermStereotype();
			        if (st != null && "Function_".equals(st.getName()) && !functions.contains(source)) {
			            functions.add(source);
			        }
			    }
			}
			return functions;
			""";

	/** Stakeholder : cas d'utilisation associes. */
	private static final String STAKEHOLDER_USE_CASES = """
			// Cas d'utilisation (Use_Case) relies a la partie prenante par une association
			if (!(cellElement instanceof IRPClass)) {
			    return null;
			}
			ArrayList<IRPModelElement> useCases = new ArrayList<IRPModelElement>();
			for (Object o : ((IRPClass) cellElement).getRelations().toList()) {
			    if (!(o instanceof IRPRelation)) {
			        continue;
			    }
			    IRPRelation relation = (IRPRelation) o;
			    if (!"Association".equals(relation.getRelationType())) {
			        continue;
			    }
			    IRPClassifier other = relation.getOtherClass();
			    IRPStereotype st = other != null ? other.getNewTermStereotype() : null;
			    if (st != null && "Use_Case".equals(st.getName()) && !useCases.contains(other)) {
			        useCases.add(other);
			    }
			}
			return useCases;
			""";

	/** Logical System : ports standards et ports SysML (Flow Port). */
	private static final String LOGICAL_SYSTEM_PORTS = """
			// Ports du systeme logique : ports standards et ports SysML (Flow Port du profil v2)
			if (!(cellElement instanceof IRPClass)) {
			    return null;
			}
			IRPClass system = (IRPClass) cellElement;
			ArrayList<IRPModelElement> ports = new ArrayList<IRPModelElement>();
			for (Object o : system.getPorts().toList()) {
			    ports.add((IRPModelElement) o);
			}
			// getPorts() ne rend pas les ports SysML : lus parmi les elements contenus (niveau 0)
			for (Object o : system.getNestedElementsByMetaClass("SysMLPort", 0).toList()) {
			    if (!ports.contains(o)) {
			        ports.add((IRPModelElement) o);
			    }
			}
			return ports;
			""";

	// ------------------------------------------------------------------ tables

	/** Motif du layout de reference, inchange. */
	public static final String OPERATIONAL_ICD_PATTERN = "Package*,{LcP}LifecyclePhase*,{CxD}ContextDiagram,"
			+ "{ref}references:.stereotype(OperationalSystem|Stakeholder),{OF}OperationalFlow,"
			+ "{refctxD}references:.stereotype(ContextDiagram)";

	/** Motif de la sous-requete des fonctions (ex FunctionQuery). */
	public static final String FUNCTION_QUERY_OLD = "FunctionQuery";
	public static final String FUNCTION_QUERY_PATTERN = "Package*,FunctionalSystem|OperationalSystem|Stakeholder,Function+";

	/** Tables de la page, dans l'ordre d'application (la reference d'abord : les ICD la copient). */
	public static final List<TableUpdate> TABLES = List.of(
			// Reference : renommee, getters communs aux trois ICD
			new TableUpdate("Operational ICD", "OperationalICDNewProfile_View", "OperationalICDNewProfile_Layout",
					"OperationalICD", null, null, null, Map.of(), icdGetters(), Map.of()),
			new TableUpdate("Use Case", "UseCaseView", "UseCaseLayout", "UseCase", null, null, null,
					Map.of(0, "Owner"),
					Map.of(1, new Getter("LifeCycle Phase", LIST, UC_LIFECYCLE_PHASE),
							3, new Getter("Operational System (Stakeholder)", LIST, UC_OPERATIONAL_SYSTEMS),
							4, new Getter("Operational Sequence Diagram", LIST, UC_SEQUENCE_DIAGRAMS),
							5, new Getter("Function", LIST, UC_FUNCTIONS),
							6, new Getter("UseCase Diagram references", LIST,
									diagramsReferencing("IRPUseCase", "Use_CaseDiagram"))), Map.of()),
			// Fonctions : sous-requete corrigee (fonctions des Functional Systems du profil v2)
			new TableUpdate("Function covered by Use Case", "FunctionCoveredByUseCaseView",
					"FunctionCoveredbyUseCaseLayout", "FunctionCoveredByUseCase", null, null, null,
					Map.of(0, "Owner"),
					Map.of(3, new Getter("Appears in Use Case Diagram", LIST,
							diagramsReferencing("IRPClass", "Use_CaseDiagram"))), Map.of()),
			new TableUpdate("Stakeholders", "StakeholderView", "StakeholderLayout", "Stakeholder", null, null, null,
					Map.of(),
					Map.of(2, new Getter("UseCase", LIST, STAKEHOLDER_USE_CASES),
							3, new Getter("UseCase diagram references", LIST,
									diagramsReferencing("IRPClass", "Use_CaseDiagram"))), Map.of()),
			// Modes : etats du New Term Operating Mode, fonctions activees (Activated Function)
			new TableUpdate("Mode and Function Traceability", "ModeAndFunctionTraceabilityView",
					"ModeAndFunctionTraceabilityLayout", "ModeAndFunctionTraceability", null, null,
					"Package*,{Sys}OperationalSystem|Stakeholder,OperatingModeChart,{State}OperatingMode,"
							+ "{dep}ActivatedFunction",
					Map.of(), Map.of(), Map.of()),
			new TableUpdate("Logical System", "LogicalSystemView", "LogicalSystemLayout", "LogicalSystem", null, null,
					null, Map.of(), Map.of(2, new Getter("Ports", LIST, LOGICAL_SYSTEM_PORTS)), Map.of()),
			// Fonctions des systemes logiques, a tous les niveaux de decomposition, et leurs ports
			new TableUpdate("Logical System Function", "LogicalSystemFunctionView", "LogicalSystemFunctionLayout",
					"LogicalSystemFunction", null, null,
					"Package+,{Ls}LogicalSystem+,{fct}Function,{arg}Flow_Port,{atype}type:",
					Map.of(3, "Function port", 4, "Port type"), Map.of(), Map.of()),
			// Logical ICD : copie de la reference, partant des Logical Architecture Diagrams
			new TableUpdate("Logical ICD", "LogicalICDLayout_2_NEWPROFILE_VIEW", null, "LogicalICD", "OperationalICD",
					"LogicalICDLayout_2_NEWPROFILE_layout",
					"Package*,{LS}LogicalSystem*,{LAD}LogicalArchitectureDiagram,"
							+ "{ref}references:.stereotype(LogicalSystem),{LF}LogicalFlow,"
							+ "{refLAD}references:.stereotype(LogicalArchitectureDiagram)",
					Map.of(0, "Logical System", 1, "Logical Architecture Diagram"), Map.of(),
					Map.of("OF", "LF", "refctxD", "refLAD")),
			new TableUpdate("Technical Component", "TechnicalComponentView", "TechnicalComponentLayout",
					"TechnicalComponent", null, null, null, Map.of(), Map.of(), Map.of()),
			// Technical ICD : copie de la reference, flux Technical Connection et Technical Flow
			new TableUpdate("Technical ICD", "TechnicalICDView", null, "TechnicalICD", "OperationalICD",
					"TechnicalICDLayout",
					"Package*,{TC}TechnicalComponent*,{TAD}TechnicalArchitectureDiagram,"
							+ "{ref}references:.stereotype(TechnicalComponent),{TF}TechnicalConnection|TechnicalFlow,"
							+ "{refTAD}references:.stereotype(TechnicalArchitectureDiagram)",
					Map.of(0, "Technical Component", 1, "Technical Architecture Diagram"), Map.of(),
					Map.of("OF", "TF", "refctxD", "refTAD")),
			new TableUpdate("Logical Technical Systems Realization", "Logical Technical Systems Realization",
					"Logical Technical Systems Realization", "LogicalTechnicalSystemsRealization", null, null, null,
					Map.of(), Map.of(), Map.of()),
			// Realisation de port : stereotype Realization (ancien profil) -> Logical Port Realization
			new TableUpdate("Technical Ports Realization", "Technical Ports Realization", "Technical Ports Realization",
					"TechnicalPortsRealization", null, null,
					"{pkg}Package+,{TC}TechnicalComponent+,{TP}TechnicalPort,"
							+ "{dep}references:.metaclass(Dependency).stereotype(LogicalPortRealization),"
							+ "{RealizedLP}to:,{LS}owner:",
					Map.of(3, "Logical System"), Map.of(), Map.of()));

	/** Labels utilises dans un motif : "{xx}" en tete d'un terme. */
	public static List<String> labels(String pattern) {
		return java.util.regex.Pattern.compile("\\{([^}]*)\\}").matcher(pattern).results().map(m -> m.group(1))
				.toList();
	}

	/** Table d'une racine de nom (TABLES) ou null. */
	public static TableUpdate byBase(String base) {
		return TABLES.stream().filter(t -> t.base().equals(base)).findFirst().orElse(null);
	}
}
