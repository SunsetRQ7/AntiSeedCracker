package me.sunsetrqsv.antiseedcracker.util;

import java.util.Locale;

/**
 * Shared command-string normalisation used by every command-blocking listener.
 *
 * <p>Also unwraps {@code /execute ... run <command>} chains. Vanilla's {@code /execute}
 * re-dispatches its trailing command internally without re-firing
 * {@code PlayerCommandPreprocessEvent}, so a naive literal-prefix check on the typed
 * message lets {@code /execute run seed} (or deeper nesting, e.g.
 * {@code /execute as @s run execute run seed}) sail straight past a blocker that only
 * looks at the outer command. Every blocking listener must check the resolved target
 * returned here, not just the raw typed message.</p>
 */
public final class CommandUtil {

    private static final int MAX_EXECUTE_DEPTH = 16;

    private CommandUtil() {}

    /**
     * Strips a leading slash and, if present, a leading {@code namespace:} prefix on the
     * root command token (not a colon anywhere later in the string — e.g. the
     * {@code minecraft:stronghold} argument of {@code /locate structure minecraft:stronghold}
     * must survive intact), then lower-cases the result.
     */
    public static String normalizeRoot(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("/")) s = s.substring(1);

        int firstSpace = s.indexOf(' ');
        int colon = s.indexOf(':');
        if (colon >= 0 && (firstSpace < 0 || colon < firstSpace)) {
            s = s.substring(colon + 1);
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    /** True if {@code normalized} is exactly {@code word} or starts with {@code word} followed by whitespace. */
    public static boolean startsWithWord(String normalized, String word) {
        if (!normalized.startsWith(word)) return false;
        return normalized.length() == word.length()
                || Character.isWhitespace(normalized.charAt(word.length()));
    }

    /**
     * If {@code normalized} is an {@code execute} invocation with a trailing {@code run}
     * clause, recursively resolves and returns the ultimate command it dispatches to
     * (normalized the same way). Returns {@code null} if the input isn't an execute
     * chain at all, or an execute chain with no resolvable {@code run} clause (e.g. a
     * bare/invalid {@code /execute}).
     */
    public static String resolveExecuteTarget(String normalized) {
        String current = normalized;
        for (int depth = 0; depth < MAX_EXECUTE_DEPTH; depth++) {
            if (!startsWithWord(current, "execute")) {
                return depth == 0 ? null : current;
            }
            int runIdx = findTopLevelRun(current);
            if (runIdx < 0) {
                return depth == 0 ? null : current;
            }
            current = normalizeRoot(current.substring(runIdx + 3));
        }
        return current;
    }

    /**
     * Finds the index of the top-level, standalone {@code run} keyword that starts the
     * command executed by an {@code /execute} chain — i.e. not one that happens to
     * appear as part of a selector argument such as {@code @e[name=run]}. Bracket/brace
     * depth is tracked so occurrences inside {@code [...]} or {@code {...}} are ignored.
     */
    private static int findTopLevelRun(String s) {
        int depth = 0;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                if (depth > 0) depth--;
            } else if (depth == 0 && c == 'r' && s.regionMatches(i, "run", 0, 3)) {
                boolean leftBoundary  = (i == 0) || Character.isWhitespace(s.charAt(i - 1));
                boolean rightBoundary = (i + 3 == n) || Character.isWhitespace(s.charAt(i + 3));
                if (leftBoundary && rightBoundary) {
                    return i;
                }
            }
        }
        return -1;
    }
}
