package utils;

import java.util.*;

public class PathUtils {
    // Helper: returns the base element (before ".")
    private static String base(String element) {
        return element != null && element.contains(".")
                ? element.split("\\.")[0]
                : element;
    }

    /**
     * Checks if the shorter path is fully contained in the longer path,
     * automatically determining which one is shorter. Flowports are handled correctly.
     *
     * @param pathA First path (list of strings)
     * @param pathB Second path (list of strings)
     * @return true if the shorter path is contained in the longer one
     */
    public static boolean isPathContained(List<String> pathA, List<String> pathB) {
        if (pathA == null || pathB == null) return false;

        // Determine which is shorter
        List<String> shorter = pathA.size() <= pathB.size() ? pathA : pathB;
        List<String> longer  = pathA.size() > pathB.size() ? pathA : pathB;

        for (int i = 0; i < shorter.size(); i++) {
            if (!Objects.equals(base(shorter.get(i)), base(longer.get(i)))) {
                return false;
            }
        }
        return true;
    }

}
