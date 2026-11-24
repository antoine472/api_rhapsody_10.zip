package test.manual;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan de rangement du package Monitoring et d'alignement de la page Home.
 * Donnees pures et calcul de placement : aucune dependance a Rhapsody, ce qui
 * permet de tester le plan sans Rhapsody (voir MonitoringPlanTest).
 *
 * <p><b>Rangement</b> (decide d'apres un controle statique des tables du profil) :</p>
 * <ul>
 *   <li>OK : tables dont le layout existe, dont les types d'elements ou le motif
 *       de contexte n'utilisent que des termes du profil actuel, et dont tous
 *       les getters Java compilent. Sous-dossiers : Diagram Indexes (une table
 *       par type de diagramme, rangees par niveau A a D), Analysis Tables Page
 *       (tables de la page Analysis Tables) et Other Tables ;</li>
 *   <li>NOK : tables sans layout ou fondees sur des termes de l'ancien profil
 *       (Logical Port, Continuous Link, Discrete Link, Context, State Diagram),
 *       et l'ancienne copie de la page Home ;</li>
 *   <li>les tables de la page Analysis Tables sont ensuite mises a jour et renommees
 *       (suffixe _SafranMLv2) par UpdateAnalysisTables ;</li>
 *   <li>TBD : contenu actuel inchange, plus le layout sans vue
 *       "Safran Architecture Model".</li>
 * </ul>
 * <p>Le contenu deja range a la main dans OK, NOK et TBD n'est pas deplace.</p>
 */
public final class MonitoringPlan {

	private MonitoringPlan() {
	}

	/** Dossier du package Monitoring qui recoit les tables qui fonctionnent. */
	public static final String OK = "OK";
	public static final String NOK = "NOK";
	public static final String TBD = "TBD";

	/** Sous-dossiers de OK. */
	public static final String DIAGRAM_INDEXES = "Diagram Indexes";
	public static final String ANALYSIS_PAGE = "Analysis Tables Page";
	public static final String OTHER_TABLES = "Other Tables";

	/** Dossier des images de la page Home, relatif au dossier _rpy du profil. */
	public static final String IMAGE_DIR = "..\\Icons\\Home\\";

	/** Vue modele des nouvelles tables : colonnes generiques "Owner" et "Name". */
	public static final String TEMPLATE_VIEW = "Logical Sequence diagrams";

	/** Nature d'un element a deplacer (pour le retrouver par son nom ET son type). */
	public enum Kind { VIEW, LAYOUT, DIAGRAM }

	/** Deplacement d'un element de la racine de Monitoring vers un dossier (chemin "OK/Other Tables"). */
	public record Move(String name, Kind kind, String folder) {
	}

	/**
	 * Table d'index d'un type de diagramme, posee comme vignette sur la page Home.
	 *
	 * @param level       dossier de niveau ("A - Operational"...) et bande de la page Home
	 * @param view        nom de la vue de table (et texte de la vignette)
	 * @param elementType libelle du New Term liste par la table
	 * @param image       image de la vignette (nom de fichier dans Icons/Home),
	 *                    null pour garder l'image actuelle
	 * @param created     true si la table n'existe pas encore et doit etre creee
	 */
	public record DiagramIndex(String level, String view, String elementType, String image, boolean created) {

		/** Nom du layout cree pour une nouvelle table : libelle sans espaces + "Layout". */
		public String layoutName() {
			return elementType.replace(" ", "") + "Layout";
		}
	}

	/** Bandes de la page Home, dans l'ordre vertical. */
	public static final String[] LEVELS = {
			"A - Operational", "B - Functional", "C - Logical", "D - Technical" };

	/** Une table d'index par type de diagramme du profil ; ordre = ordre des vignettes dans la bande. */
	public static final List<DiagramIndex> DIAGRAM_INDEX_TABLES = List.of(
			// Bande A : analyse operationnelle
			new DiagramIndex(LEVELS[0], "Lifecycle diagrams", "Lifecycle Diagram", null, false),
			new DiagramIndex(LEVELS[0], "Domain Diagram", "Domain Diagram", null, false),
			new DiagramIndex(LEVELS[0], "Use Case diagrams", "Use_Case Diagram", null, false),
			new DiagramIndex(LEVELS[0], "Context Diagram", "Context Diagram", null, false),
			new DiagramIndex(LEVELS[0], "Environment diagrams", "Environment Diagram", "EnvironmentDiagram.jpg", true),
			new DiagramIndex(LEVELS[0], "Operational Sequence diagrams", "Operational Sequence Diagram", null, false),
			new DiagramIndex(LEVELS[0], "Operational Flowchart diagrams", "Operational Flowchart Diagram",
					"OperationalFlowchartDiagram.jpg", true),
			// Bande B : architecture fonctionnelle
			new DiagramIndex(LEVELS[1], "Modes diagram", "Operating Mode Chart", null, false),
			new DiagramIndex(LEVELS[1], "Functional Breakdown Structure", "Functional Breakdown Structure", null, false),
			new DiagramIndex(LEVELS[1], "System Functional Behavior diagrams", "System Functional Behavior",
					"SystemFunctionalBehavior.jpg", true),
			new DiagramIndex(LEVELS[1], "Functional Behavior diagrams", "Functional Behavior Diagram", null, false),
			new DiagramIndex(LEVELS[1], "Functional Chain diagrams", "Functional Chain Diagram",
					"FunctionalChainDiagram.jpg", true),
			new DiagramIndex(LEVELS[1], "Functional Sequence diagrams", "Functional Sequence Diagram",
					"FunctionalSequenceDiagram.jpg", true),
			// Bande C : conception logique (les flux et leur composition y sont rattaches)
			new DiagramIndex(LEVELS[2], "Logical Breakdown Structure", "Logical Breakdown Structure", null, false),
			// image actuelle : chemin absolu sur un poste, remplace par l'image du dossier Icons/Home
			new DiagramIndex(LEVELS[2], "Logical Architecture Diagram", "Logical Architecture Diagram",
					"LogicalStructureDiagrams.jpg", false),
			new DiagramIndex(LEVELS[2], "Logical Interfaces diagrams", "Logical Interfaces Diagram",
					"LogicalInterfacesDiagram.jpg", true),
			new DiagramIndex(LEVELS[2], "Logical Sequence diagrams", "Logical Sequence Diagram", null, false),
			new DiagramIndex(LEVELS[2], "Flow Composition diagrams", "Flow Composition Diagram",
					"FlowCompositionDiagram.jpg", true),
			// Bande D : conception technique (deux images en chemin absolu, corrigees)
			new DiagramIndex(LEVELS[3], "Technical Breakdown Structure", "Technical Breakdown Structure",
					"ProductBreakdownStructure.jpg", false),
			new DiagramIndex(LEVELS[3], "Technical Architecture diagrams", "Technical Architecture Diagram",
					"PhysicalStructureDiagrams.jpg", false),
			new DiagramIndex(LEVELS[3], "Technical Interfaces diagrams", "Technical Interfaces Diagram",
					"TechnicalInterfacesDiagram.jpg", true),
			new DiagramIndex(LEVELS[3], "Technical Flows diagrams", "Technical Flows Diagram",
					"TechnicalFlowsDiagram.jpg", true));

	/** Chemin du dossier d'un niveau : "OK/Diagram Indexes/A - Operational". */
	public static String levelFolder(String level) {
		return OK + "/" + DIAGRAM_INDEXES + "/" + level;
	}

	/** Deplacements des elements poses a la racine de Monitoring. */
	public static List<Move> moves() {
		List<Move> moves = new ArrayList<>();
		// Tables d'index existantes : dans le dossier de leur niveau
		for (DiagramIndex d : DIAGRAM_INDEX_TABLES) {
			if (!d.created()) moves.add(new Move(d.view(), Kind.VIEW, levelFolder(d.level())));
		}
		// Tables de la page Analysis Tables qui fonctionnent
		String page = OK + "/" + ANALYSIS_PAGE;
		for (String v : List.of("StakeholderView", "LogicalSystemView", "UseCaseView",
				"FunctionCoveredByUseCaseView", "Logical Technical Systems Realization", "Technical Ports Realization",
				"ModeAndFunctionTraceabilityView", "LogicalSystemFunctionView", "TechnicalComponentView",
				"TechnicalICDView", "OperationalICDNewProfile_View",
				// devient la table Logical ICD de la page (voir AnalysisTablesPlan)
				"LogicalICDLayout_2_NEWPROFILE_VIEW")) {
			moves.add(new Move(v, Kind.VIEW, page));
		}
		// Sous-requete de FunctionCoveredbyUseCaseLayout : rangee avec elle
		moves.add(new Move("FunctionQuery", Kind.LAYOUT, page));
		// Autres tables qui fonctionnent, sans vignette
		for (String v : List.of("FICD", "Acronym Table View", "Function", "Function Flow", "System Flow")) {
			moves.add(new Move(v, Kind.VIEW, OK + "/" + OTHER_TABLES));
		}
		// Tables qui ne fonctionnent pas avec le profil actuel
		for (String v : List.of("LICDView", "States Diagrams", "OperationalLogicalICDLayout", "Logical ICD",
				"Circuit diagrams", "OperationalICDView", "LogicalICDView")) {
			moves.add(new Move(v, Kind.VIEW, NOK));
		}
		// Ancienne copie de la page Home (13 vignettes, plus maintenue)
		moves.add(new Move("Home Page Diagram", Kind.DIAGRAM, NOK));
		// Layout sans vue : decision a prendre
		moves.add(new Move("Safran Architecture Model", Kind.LAYOUT, TBD));
		return moves;
	}

	// ---------------------------------------------------------------- page Home

	/** Rectangle d'une vignette, en coordonnees du diagramme. */
	public record Tile(int x, int y, int w, int h) {
	}

	/** Haut de chaque bande de la page Home (lu dans le diagramme : 4 bandes de 213). */
	public static final int[] BAND_TOP = { 5, 218, 431, 644 };
	public static final int BAND_HEIGHT = 213;
	/** Hauteur du bandeau de titre en haut de chaque bande. */
	public static final int TITLE_HEIGHT = 40;
	/** Bornes horizontales des bandes (la colonne de droite commence a x = 995). */
	public static final int BAND_LEFT = 6;
	public static final int BAND_RIGHT = 966;
	/** Marge interieure, ecart entre vignettes, largeur maximale et rapport hauteur/largeur. */
	public static final int MARGIN = 8;
	public static final int GAP = 14;
	public static final int MAX_TILE_WIDTH = 140;
	public static final int TILE_HEIGHT = 100;

	/**
	 * Place n vignettes de meme taille sur une ligne, centrees dans la bande
	 * (horizontalement entre les bornes, verticalement sous le bandeau de titre).
	 *
	 * @param band rang de la bande (0 = A ... 3 = D)
	 * @param n    nombre de vignettes
	 * @return les rectangles, de gauche a droite
	 */
	public static List<Tile> layoutBand(int band, int n) {
		List<Tile> tiles = new ArrayList<>();
		if (n <= 0) return tiles;
		int usable = BAND_RIGHT - BAND_LEFT - 2 * MARGIN;
		// largeur commune : la plus grande qui fait tenir n vignettes et n-1 ecarts
		int w = Math.min(MAX_TILE_WIDTH, (usable - (n - 1) * GAP) / n);
		int total = n * w + (n - 1) * GAP;
		int x0 = BAND_LEFT + (BAND_RIGHT - BAND_LEFT - total) / 2;
		// zone libre sous le bandeau de titre
		int top = BAND_TOP[band] + TITLE_HEIGHT;
		int y = top + (BAND_HEIGHT - TITLE_HEIGHT - TILE_HEIGHT) / 2;
		for (int i = 0; i < n; i++) {
			tiles.add(new Tile(x0 + i * (w + GAP), y, w, TILE_HEIGHT));
		}
		return tiles;
	}

	/** Tables d'index d'une bande, dans l'ordre des vignettes. */
	public static List<DiagramIndex> tablesOfBand(int band) {
		List<DiagramIndex> list = new ArrayList<>();
		for (DiagramIndex d : DIAGRAM_INDEX_TABLES) {
			if (d.level().equals(LEVELS[band])) list.add(d);
		}
		return list;
	}
}
