package com.qsr.customspd.tools.deletionaudit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compares two {@link CallableInventory} snapshots and reports removals.
 *
 * <p>Deliberately one-directional: additions and growth are api-diff's
 * concern. This tool exists to answer one question — did anything quietly
 * disappear?
 */
public final class InventoryDiff {

    private InventoryDiff() {
    }

    /** A callable that existed at the base ref and does not exist at the head ref. */
    public record Deleted(String key, String visibility, int statementsLost) {
    }

    /** A callable that kept its signature but whose body lost statements. */
    public record Shrunk(String key, int before, int after) {
    }

    public record Report(List<Deleted> deleted, List<Shrunk> shrunk) {
        public boolean isEmpty() {
            return deleted.isEmpty() && shrunk.isEmpty();
        }
    }

    /**
     * @param minShrink minimum statement drop before a surviving callable is
     *                  reported; filters formatting-level noise out of a
     *                  whole-repository run
     */
    public static Report compare(CallableInventory before, CallableInventory after, int minShrink) {
        Map<String, CallableInventory.Entry> beforeByKey = before.byKey();
        Map<String, CallableInventory.Entry> afterByKey = after.byKey();

        List<Deleted> deleted = new ArrayList<>();
        List<Shrunk> shrunk = new ArrayList<>();

        for (Map.Entry<String, CallableInventory.Entry> entry : beforeByKey.entrySet()) {
            CallableInventory.Entry priorEntry = entry.getValue();
            CallableInventory.Entry currentEntry = afterByKey.get(entry.getKey());

            if (currentEntry == null) {
                deleted.add(new Deleted(
                        entry.getKey(), priorEntry.visibility(), priorEntry.statementCount()));
            } else if (priorEntry.statementCount() - currentEntry.statementCount() >= minShrink) {
                shrunk.add(new Shrunk(
                        entry.getKey(), priorEntry.statementCount(), currentEntry.statementCount()));
            }
        }
        return new Report(deleted, shrunk);
    }
}
