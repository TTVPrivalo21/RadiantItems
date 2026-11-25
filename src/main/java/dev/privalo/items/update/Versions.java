package dev.privalo.items.update;

import java.util.Locale;

/**
 * Semantic-version helpers: "v1.2.0" → "1.2.0", numeric comparison of each part
 * ("1.10.0" &gt; "1.9.2"), a release is newer than its pre-releases ("1.2.0" &gt; "1.2.0-beta.1")
 * and build metadata ("+build.5") is ignored.
 */
public final class Versions {

    private Versions() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    /** Trims, removes a leading "v"/"V" (also "version-"/"release-") and build metadata. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String v = raw.trim();
        String lower = v.toLowerCase(Locale.ROOT);
        for (String prefix : new String[]{"version-", "release-", "version", "v"}) {
            if (lower.startsWith(prefix) && lower.length() > prefix.length() && Character.isDigit(lower.charAt(prefix.length()))) {
                v = v.substring(prefix.length());
                break;
            }
        }
        int plus = v.indexOf('+');
        return plus >= 0 ? v.substring(0, plus) : v;
    }

    /** &lt; 0 when a is older than b, 0 when equal, &gt; 0 when a is newer. */
    public static int compare(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        String[] sa = na.split("-", 2);
        String[] sb = nb.split("-", 2);
        int core = compareCore(sa[0], sb[0]);
        if (core != 0) {
            return core;
        }
        boolean preA = sa.length > 1 && !sa[1].isEmpty();
        boolean preB = sb.length > 1 && !sb[1].isEmpty();
        if (!preA && !preB) {
            return 0;
        }
        if (!preA) {
            return 1;
        }
        if (!preB) {
            return -1;
        }
        return comparePre(sa[1], sb[1]);
    }

    public static boolean isNewer(String remote, String installed) {
        return compare(remote, installed) > 0;
    }

    private static int compareCore(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            long x = i < pa.length ? number(pa[i]) : 0;
            long y = i < pb.length ? number(pb[i]) : 0;
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        return 0;
    }

    /** Pre-release identifiers: numeric ones compare as numbers and sort before text ones (SemVer §11). */
    private static int comparePre(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            boolean na = pa[i].matches("\\d+");
            boolean nb = pb[i].matches("\\d+");
            int c;
            if (na && nb) {
                c = Long.compare(Long.parseLong(pa[i]), Long.parseLong(pb[i]));
            } else if (na) {
                c = -1;
            } else if (nb) {
                c = 1;
            } else {
                c = pa[i].compareToIgnoreCase(pb[i]);
            }
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }

    /** Leading digits of a part ("2b" → 2, "x" → 0). */
    private static long number(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Long.parseLong(part.substring(0, Math.min(end, 18)));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
