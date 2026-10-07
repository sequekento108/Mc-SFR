package dev.freedomindex;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rough classifier for license strings found in fabric.mod.json.
 * Handles SPDX expressions (OR / AND) and the sloppy free-text strings people actually write.
 * This is a heuristic, not legal advice. RMS would want you to read the license yourself.
 */
public final class LicenseClassifier {

    /** Ordered best to worst. */
    public enum Category {
        COPYLEFT("copyleft"),
        PERMISSIVE("free, permissive"),
        UNKNOWN("unknown / unclear"),
        NONFREE("NONFREE");

        public final String label;
        Category(String label) { this.label = label; }
        public boolean isFree() { return this == COPYLEFT || this == PERMISSIVE; }
    }

    private LicenseClassifier() {}

    /** Multiple entries in fabric.mod.json's "license" array are treated as alternatives (best one wins). */
    public static Category classify(Collection<String> licenses) {
        if (licenses == null || licenses.isEmpty()) return Category.UNKNOWN;
        Category best = Category.NONFREE;
        for (String l : licenses) best = better(best, classifyExpression(l));
        return best;
    }

    private static Category classifyExpression(String expr) {
        if (expr == null || expr.isBlank()) return Category.UNKNOWN;
        String e = expr.replace("(", " ").replace(")", " ");
        Category best = Category.NONFREE;
        for (String orPart : e.split("(?i)\\sOR\\s")) {
            Category worst = Category.COPYLEFT;
            for (String andPart : orPart.split("(?i)\\sAND\\s")) {
                worst = worse(worst, classifyAtom(andPart));
            }
            best = better(best, worst);
        }
        return best;
    }

    private static Category classifyAtom(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return Category.UNKNOWN;
        s = s.replaceAll("\\swith\\s.*$", ""); // SPDX exceptions

        // Nonfree first, so "CC-BY-NC-SA" isn't mistaken for CC-BY-SA
        if (s.contains("all rights reserved") || has(s, "arr") || s.contains("proprietary")
                || s.contains("-nc") || s.contains("-nd") || s.contains("non-commercial")
                || s.contains("noncommercial") || s.contains("no license") || s.contains("custom")
                || s.contains("eula")) {
            return Category.NONFREE;
        }

        if (s.contains("gpl") || s.contains("mozilla") || has(s, "mpl") || s.contains("eupl")
                || s.contains("cc-by-sa") || s.contains("cddl") || s.contains("eclipse public")
                || has(s, "epl") || s.contains("osl") || s.contains("copyleft")) {
            return Category.COPYLEFT;
        }

        if (has(s, "mit") || s.contains("apache") || has(s, "bsd") || has(s, "isc") || has(s, "zlib")
                || s.contains("unlicense") || s.contains("cc0") || s.contains("public domain")
                || s.contains("wtfpl") || s.contains("cc-by") || s.contains("artistic")
                || s.contains("boost")) {
            return Category.PERMISSIVE;
        }
        return Category.UNKNOWN;
    }

    private static boolean has(String s, String word) {
        return Pattern.compile("(^|[^a-z])" + Pattern.quote(word) + "([^a-z]|$)").matcher(s).find();
    }

    private static Category better(Category a, Category b) { return a.ordinal() <= b.ordinal() ? a : b; }
    private static Category worse(Category a, Category b) { return a.ordinal() >= b.ordinal() ? a : b; }
}
