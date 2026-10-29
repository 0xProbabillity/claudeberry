package berryssh.device;

/**
 * The arithmetic behind redrawing only what changed and sliding a scroll.
 *
 * Kept apart from {@link TerminalCanvas}, which needs MIDP, so it can be
 * tested on the host against real terminal output.
 *
 * Written for -source 1.3: no generics, no enhanced for, no StringBuilder.
 */
public final class RowDiff {

    private RowDiff() {
    }

    /**
     * A row's identity: every cell, character and attributes, plus the cursor
     * when it is on this row. Equal hashes mean the same picture.
     */
    public static long hash(long[] cells, int columns, int cursorColumn) {
        long h = 17;
        if (cells != null) {
            for (int column = 0; column < columns && column < cells.length; column++) {
                h = h * 31 + cells[column];
            }
        }
        if (cursorColumn >= 0) {
            h = h * 31 + 1000 + cursorColumn;
        }
        return h;
    }

    /** True for a row with nothing on it, which matches any other empty row. */
    public static boolean blank(long[] cells, int columns) {
        if (cells == null) {
            return true;
        }
        for (int column = 0; column < columns && column < cells.length; column++) {
            long cell = cells[column];
            if ((char) cell > 32 || (cell >>> 32) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * How many rows the screen scrolled up by, or zero.
     *
     * A distance counts when at least two thirds of the non-blank rows it
     * compares, and at least three of them, are the rows that many places
     * below a moment ago. Blank rows are left out: every empty line hashes
     * the same, and a screen of them would match any shift at all.
     */
    public static int scrollDistance(long[] now, boolean[] nowBlank,
                                     long[] before, boolean[] beforeBlank, boolean[] beforeDrawn) {
        int rows = now.length;
        int best = 0;
        int bestMatches = 0;
        for (int k = 1; k < rows - 1; k++) {
            int matches = 0;
            int compared = 0;
            for (int row = 0; row + k < rows; row++) {
                if (nowBlank[row] || !beforeDrawn[row + k] || beforeBlank[row + k]) {
                    continue;
                }
                compared++;
                if (now[row] == before[row + k]) {
                    matches++;
                }
            }
            if (compared >= 3 && matches * 3 >= compared * 2 && matches > bestMatches) {
                best = k;
                bestMatches = matches;
            }
        }
        return best;
    }

    /** How long a slide of this many rows takes, in milliseconds. */
    public static int slideDuration(int rows) {
        return Math.min(320, 140 + 30 * rows);
    }

    /**
     * Pixels still to travel, from the full distance at the start to none at
     * the end, along a smoothstep: gentle at both ends, like a hand scroll.
     */
    public static int slideOffset(long elapsed, int duration, int distance) {
        if (elapsed <= 0) {
            return distance;
        }
        if (elapsed >= duration) {
            return 0;
        }
        int t = (int) (elapsed * 1000 / duration);
        int eased = t * t * (3000 - 2 * t) / 1000000;
        return distance * (1000 - eased) / 1000;
    }
}
