package utils;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcule la largeur d'un bloc de diagramme pour que son nom tienne sur une
 * seule ligne.
 * <p>
 * Pur Java (aucun appel a l'API Rhapsody, aucun affichage necessaire) : le
 * texte est mesure avec la police indiquee, puis converti en unites du
 * diagramme. Testable en JUnit sans Rhapsody.
 * </p>
 *
 * <p><b>Calcul</b> : largeur = texte (en pixels) + 2 x {@link #SIDE_PADDING}
 * + {@link #ICON_SPACE} (icone du new term dans le coin du bloc), arrondie a la
 * dizaine superieure, puis ramenee entre {@link #MIN_WIDTH} et {@link #MAX_WIDTH}.</p>
 *
 * <p><b>Unites</b> : la taille de police Rhapsody est en points ; a 100 % de
 * zoom, une unite de diagramme vaut un pixel a 96 dpi, d'ou le facteur 96 / 72.
 * Calibrage verifie sur un diagramme LBS reel : "logicalsystem_10" en Arial 12
 * gras passe sur deux lignes dans un bloc de 150 et tient sur une ligne dans
 * un bloc de 204 ; le calcul donne 180.</p>
 */
public final class BlockTextFit {

	/** Largeur minimale d'un bloc ajuste. */
	public static final int MIN_WIDTH = 100;

	/** Largeur maximale d'un bloc ajuste. */
	public static final int MAX_WIDTH = 1000;

	/** Marge entre le texte et chaque bord du bloc. */
	public static final int SIDE_PADDING = 10;

	/** Place reservee a l'icone du new term, dans le coin haut droit du bloc. */
	public static final int ICON_SPACE = 24;

	/** La largeur calculee est arrondie au multiple superieur de cette valeur. */
	public static final int ROUND_TO = 10;

	/** Conversion points vers pixels a 96 dpi (zoom 100 % de Rhapsody). */
	private static final double POINTS_TO_PIXELS = 96.0 / 72.0;

	/** Contexte de mesure sans ecran : utilisable meme sans affichage. */
	private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

	private BlockTextFit() {
		// classe utilitaire : pas d'instance
	}

	/**
	 * Police du nom d'un bloc.
	 *
	 * @param fontName nom de la police (ex. "Arial")
	 * @param sizePt   taille en points
	 * @param bold     vrai si le nom est en gras
	 */
	public record TextStyle(String fontName, int sizePt, boolean bold) {

		private static final String DEFAULT_FONT = "Arial";
		private static final int DEFAULT_SIZE = 12;

		/** Valeurs par defaut du profil Safran pour les blocs : Arial 12 gras. */
		public static final TextStyle DEFAULT = new TextStyle(DEFAULT_FONT, DEFAULT_SIZE, true);

		/** Police vide ou taille invalide : valeur par defaut. */
		public TextStyle {
			if (fontName == null || fontName.isBlank()) fontName = DEFAULT_FONT;
			if (sizePt <= 0) sizePt = DEFAULT_SIZE;
		}
	}

	/**
	 * Noms de metaclasse a essayer, dans l'ordre, pour lire la police d'un bloc
	 * dans les proprietes "Format.&lt;metaclasse&gt;.Font.*" du profil :
	 * <ol>
	 *   <li>le new term sans espaces : "Logical System" donne "LogicalSystem",
	 *       "Technical Component" donne "TechnicalComponent" ;</li>
	 *   <li>le meme suivi de "_" : "Function" donne "Function_" (nom utilise
	 *       par le profil, "Function" etant reserve) ;</li>
	 *   <li>la metaclasse Rhapsody, par exemple "Class".</li>
	 * </ol>
	 *
	 * @param userDefinedMetaClass new term du bloc (peut etre vide)
	 * @param metaClass            metaclasse Rhapsody du bloc (peut etre vide)
	 */
	public static List<String> formatMetaclassKeys(String userDefinedMetaClass, String metaClass) {
		List<String> keys = new ArrayList<>();
		if (userDefinedMetaClass != null && !userDefinedMetaClass.isBlank()) {
			String compact = userDefinedMetaClass.replace(" ", "");
			keys.add(compact);
			keys.add(compact + "_");
		}
		if (metaClass != null && !metaClass.isBlank() && !keys.contains(metaClass)) {
			keys.add(metaClass);
		}
		return keys;
	}

	/** Largeur du texte en pixels (unites du diagramme a 100 %). */
	public static double textWidthPx(String text, TextStyle style) {
		if (text == null || text.isEmpty()) return 0;
		Font font = new Font(style.fontName(), style.bold() ? Font.BOLD : Font.PLAIN, style.sizePt());
		return font.getStringBounds(text, FRC).getWidth() * POINTS_TO_PIXELS;
	}

	/**
	 * Largeur de bloc permettant d'afficher {@code text} sur une seule ligne.
	 *
	 * @return largeur arrondie a la dizaine superieure, entre MIN_WIDTH et MAX_WIDTH
	 */
	public static int fittedWidth(String text, TextStyle style) {
		double raw = textWidthPx(text, style) + 2 * SIDE_PADDING + ICON_SPACE;
		int rounded = (int) Math.ceil(raw / ROUND_TO) * ROUND_TO;
		return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, rounded));
	}
}
