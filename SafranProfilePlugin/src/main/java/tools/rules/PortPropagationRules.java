package tools.rules;

import java.util.Arrays;
import java.util.List;

/**
 * Règles pures (sans API Rhapsody) pour :
 * - calculer la direction du port target
 * - calculer la direction du flow ("toEnd1", "toEnd2", "bidirectional") en fonction du port cliqué
 */
public final class PortPropagationRules {

	private PortPropagationRules() {}

	// Rhapsody flow literals
	public static final String FLOW_TO_END1 = "toEnd1";
	public static final String FLOW_TO_END2 = "toEnd2";
	public static final String FLOW_BIDIR   = "bidirectional";

	// ----------------------------
	// 1) Direction port -> port
	// ----------------------------

	/** Retourne la nouvelle direction à appliquer au target (ou null => "ne rien faire"). */
	public static String computeTargetPortDirection(
			String srcDir,
			boolean srcBorder,
			boolean tgtBorder,
			String srcFullPathName,
			String tgtFullPathName
			) {
		String normSrc = normalizeDir(srcDir);
		if (normSrc == null || normSrc.isBlank()) return null;

		List<String> srcOwnerPath = containerPath(srcFullPathName);
		List<String> tgtOwnerPath = containerPath(tgtFullPathName);

		boolean sameDepth  = srcOwnerPath.size() == tgtOwnerPath.size();
		boolean sameBranch = isPrefixPath(srcOwnerPath, tgtOwnerPath) || isPrefixPath(tgtOwnerPath, srcOwnerPath);

		boolean flip;
		if (srcBorder ^ tgtBorder) {
			flip = false; // border <-> interne : on ne flip pas
		} else if (sameDepth) {
			flip = true;
		} else if (sameBranch) {
			flip = false; // contenance (ancestor/descendant)
		} else {
			flip = true;
		}

		return flip ? flipDirSafe(normSrc) : normSrc;
	}

	// ----------------------------
	// 2) Sync direction du flow
	// ----------------------------

	/**
	 * Déduit la direction du flow à partir du port cliqué.
	 *
	 * @param clickedDir direction du port cliqué ("In","Out","InOut", etc.)
	 * @param otherDir direction de l'autre port (peut être null/blank)
	 * @param clickedIsEnd1 true si clickedPort est flow.end1, false si c'est end2
	 * @param isContainment true si l'un est ancêtre de l'autre
	 * @param clickedIsAncestor si isContainment==true, true => clicked est ancêtre, false => other est ancêtre
	 * @return "toEnd1"/"toEnd2"/"bidirectional" ou null si ambigu => ne pas toucher au flow
	 */
	public static String computeFlowDirectionFromClickedPort(
			String clickedDir,
			String otherDir,
			boolean clickedIsEnd1,
			boolean isContainment,
			boolean clickedIsAncestor
			) {
		String src = normalizeDir(clickedDir);
		String oth = normalizeDir(otherDir);

		if (src == null || src.isBlank()) return null;

		// 1) InOut => bidirectional
		if (isInOut(src)) return FLOW_BIDIR;

		// Helpers : "vers clicked" ou "vers other"
		String towardClicked = clickedIsEnd1 ? FLOW_TO_END1 : FLOW_TO_END2;
		String towardOther   = clickedIsEnd1 ? FLOW_TO_END2 : FLOW_TO_END1;

		// 2) Non ambigu : Out/In => Out vers In
		if (isOut(src) && isIn(oth)) return towardOther;
		if (isIn(src) && isOut(oth)) return towardClicked;

		// 3) Ambigu : utiliser la contenance si dispo
		if (isContainment) {
			// Ancestor / Descendant du point de vue "clicked"
			boolean clickedIsDescendant = !clickedIsAncestor;

			// Règle containment (vérité = port cliqué)
			// - src Out => flow vers l'ancêtre
			// - src In  => flow vers le descendant
			if (isOut(src)) {
				return clickedIsAncestor ? towardClicked : towardOther;
			}
			if (isIn(src)) {
				return clickedIsDescendant ? towardClicked : towardOther;
			}
			return null;
		}

		// 4) Pas de contenance : si other = InOut, on suit le port cliqué
		if (isInOut(oth)) {
			if (isOut(src)) return towardOther;
			if (isIn(src))  return towardClicked;
		}

		// 5) Ambigu sans contenance => ne pas toucher
		return null;
	}

	// ----------------------------
	// Utilitaires
	// ----------------------------

	/** Container path = tout sauf le dernier segment (nom du port). */
	public static List<String> containerPath(String fullPathName) {
		if (fullPathName == null || fullPathName.isBlank()) return List.of();

		String s = fullPathName.trim();
		List<String> parts = Arrays.asList(s.split("::"));
		if (parts.isEmpty()) return List.of();

		// Cas standard : ...::owner::port
		if (parts.size() > 1) {
			String last = parts.get(parts.size() - 1);

			// Cas Rhapsody fréquent : ...::Owner.port
			int dot = last.lastIndexOf('.');
			if (dot > 0 && dot < last.length() - 1) {
				String owner = last.substring(0, dot);
				var out = new java.util.ArrayList<>(parts.subList(0, parts.size() - 1));
				out.add(owner);
				return out;
			}

			return parts.subList(0, parts.size() - 1);
		}

		// Cas sans :: mais avec dot : Owner.port
		String only = parts.get(0);
		int dot = only.lastIndexOf('.');
		if (dot > 0 && dot < only.length() - 1) {
			return List.of(only.substring(0, dot));
		}
		return List.of();
	}

	public static boolean isPrefixPath(List<String> prefix, List<String> full) {
		if (prefix == null || full == null) return false;
		if (prefix.size() > full.size()) return false;
		for (int i = 0; i < prefix.size(); i++) {
			if (!prefix.get(i).equals(full.get(i))) return false;
		}
		return true;
	}

	public static Containment containmentOf(String clickedFullPath, String otherFullPath) {
		List<String> c = containerPath(clickedFullPath);
		List<String> o = containerPath(otherFullPath);

		if (isPrefixPath(c, o) && c.size() < o.size()) return Containment.CLICKED_ANCESTOR;
		if (isPrefixPath(o, c) && o.size() < c.size()) return Containment.OTHER_ANCESTOR;
		return Containment.NONE;
	}

	public enum Containment { NONE, CLICKED_ANCESTOR, OTHER_ANCESTOR }

	public static String normalizeDir(String d) {
		if (d == null) return null;
		String s = d.trim();
		if (s.isEmpty()) return null;

		// valeurs "pas de direction"
		if ("Unspecified".equalsIgnoreCase(s) || "None".equalsIgnoreCase(s)) return null;

		if ("In".equalsIgnoreCase(s)) return "In";
		if ("Out".equalsIgnoreCase(s)) return "Out";

		// tolérances InOut
		if ("InOut".equalsIgnoreCase(s)
				|| "IN_OUT".equalsIgnoreCase(s)
				|| "In_Out".equalsIgnoreCase(s)
				|| "In/Out".equalsIgnoreCase(s)
				|| "In-Out".equalsIgnoreCase(s)
				|| "In Out".equalsIgnoreCase(s)) {
			return "InOut";
		}

		return s; // inconnu => inchangé
	}

	public static String flipDirSafe(String d) {
		String n = normalizeDir(d);
		if (n == null) return null;
		if (isInOut(n)) return n;
		if (isIn(n))  return "Out";
		if (isOut(n)) return "In";
		return n; // inconnu => inchangé
	}

	// ----------------------------
	// 2bis) Sync direction du flow - cas Border <-> Internal (Structure Diagram)
	// ----------------------------

	/**
	 * Cas spécial : exactement un port est sur le contour (border) et l'autre est interne.
	 *
	 * Règles confirmées :
	 * - Direction effective = clicked si (In/Out/InOut), sinon other si (In/Out/InOut), sinon null
	 * - InOut => bidirectional
	 * - Out   => flow vers le port border
	 * - In    => flow vers le port interne
	 *
	 * @param clickedDir direction brute du port cliqué
	 * @param otherDir direction brute de l'autre port
	 * @param clickedIsEnd1 true si clicked = end1, false si clicked = end2
	 * @param clickedIsBorder true si le port cliqué est sur le contour
	 * @return toEnd1/toEnd2/bidirectional ou null si impossible de déduire
	 */
	public static String computeFlowDirectionForBorderPair(
			String clickedDir,
			String otherDir,
			boolean clickedIsEnd1,
			boolean clickedIsBorder
			) {
		String eff = normalizeForFlowDecision(clickedDir);
		if (eff == null) eff = normalizeForFlowDecision(otherDir);
		if (eff == null) return null;

		if (isInOut(eff)) return FLOW_BIDIR;

		// déterminer quelle extrémité du flow est le border
		boolean borderIsEnd1 = clickedIsBorder ? clickedIsEnd1 : !clickedIsEnd1;

		// Out => vers border ; In => vers interne
		if (isOut(eff)) {
			return borderIsEnd1 ? FLOW_TO_END1 : FLOW_TO_END2;
		}
		if (isIn(eff)) {
			return borderIsEnd1 ? FLOW_TO_END2 : FLOW_TO_END1;
		}
		return null;
	}

	/** Pour la décision de flow, on ne considère comme "connu" que In/Out/InOut. */
	private static String normalizeForFlowDecision(String d) {
		String n = normalizeDir(d);
		if (isIn(n) || isOut(n) || isInOut(n)) return n;
		return null; // implicite / inconnu => null
	}

	public static boolean isIn(String d)    { return d != null && "In".equalsIgnoreCase(d); }
	public static boolean isOut(String d)   { return d != null && "Out".equalsIgnoreCase(d); }
	public static boolean isInOut(String d) { return d != null && "InOut".equalsIgnoreCase(d); }
}