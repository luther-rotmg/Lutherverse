package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryDiffTest {

    private static CallableInventory inventoryOf(CallableInventory.Entry... entries) {
        return new CallableInventory(List.of(entries));
    }

    @Test
    void reportsACallablePresentBeforeAndAbsentAfter() {
        CallableInventory before = inventoryOf(
                new CallableInventory.Entry("CorpseDust#actions(Hero)", "public", 5));
        CallableInventory after = inventoryOf();

        InventoryDiff.Report report = InventoryDiff.compare(before, after, 1);

        assertEquals(1, report.deleted().size());
        assertEquals("CorpseDust#actions(Hero)", report.deleted().get(0).key());
        assertEquals(5, report.deleted().get(0).statementsLost());
    }

    @Test
    void reportsABodyThatLostStatementsWhileKeepingItsSignature() {
        CallableInventory before = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 12));
        CallableInventory after = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 3));

        InventoryDiff.Report report = InventoryDiff.compare(before, after, 1);

        assertTrue(report.deleted().isEmpty());
        assertEquals(1, report.shrunk().size());
        assertEquals(12, report.shrunk().get(0).before());
        assertEquals(3, report.shrunk().get(0).after());
    }

    @Test
    void ignoresShrinkageBelowTheThreshold() {
        CallableInventory before = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 12));
        CallableInventory after = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 10));

        assertTrue(InventoryDiff.compare(before, after, 3).shrunk().isEmpty(),
                "a 2-statement drop must not fire when minShrink is 3");
    }

    @Test
    void ignoresAdditionsAndGrowth() {
        CallableInventory before = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 3));
        CallableInventory after = inventoryOf(
                new CallableInventory.Entry("Mob#die(Object)", "public", 12),
                new CallableInventory.Entry("Mob#extra()", "private", 4));

        assertTrue(InventoryDiff.compare(before, after, 1).isEmpty(),
                "this tool audits removals only; additions are api-diff's job");
    }
}
