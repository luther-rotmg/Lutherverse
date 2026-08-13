package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeneratorCategoryInserterTest {
    private static final String GEN =
            "static {\n  Category.FOOD.classes = new Class<?>[]{\n    Food.class,\n    Pasty.class\n  };\n}\n";

    @Test
    void insertsClassIntoNamedCategory() {
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(GEN, "FOOD", "Berry");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("Pasty.class,\n    Berry.class"),
                () -> r.newContent());
    }

    @Test
    void idempotent() {
        AnchorInserter.Result first = GeneratorCategoryInserter.addItem(GEN, "FOOD", "Berry");
        assertFalse(GeneratorCategoryInserter.addItem(first.newContent(), "FOOD", "Berry").inserted());
    }

    @Test
    void idempotencyDoesNotSuffixCollide() {
        // "SuperBerry.class" already present must NOT count as "Berry.class" already present.
        String gen = "static {\n  Category.FOOD.classes = new Class<?>[]{\n    Food.class,\n"
                + "    SuperBerry.class\n  };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "FOOD", "Berry");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("Berry.class"), () -> r.newContent());
    }

    @Test
    void matchesUnqualifiedAssignmentAsInTheRealGeneratorJava() {
        // The real core/.../Generator.java assigns these fields from inside Category's
        // own static block ("FOOD.classes = ..."), not "Category.FOOD.classes = ...".
        // The compile-smoke step (Task 9) caught this: the qualified-only regex never
        // matched the real file.
        String gen = "static {\n  FOOD.classes = new Class<?>[]{\n    Food.class,\n    Pasty.class\n  };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "FOOD", "Berry");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("Pasty.class,\n    Berry.class"), () -> r.newContent());
    }

    @Test
    void unknownCategoryThrows() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorCategoryInserter.addItem(GEN, "WAND", "Berry"));
    }

    @Test
    void singleLineArrayKeepsExistingEntries() {
        String gen = "static {\n  Category.WEAPON.classes = new Class<?>[]{ Dagger.class, Sword.class };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "WEAPON", "Mace");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("Dagger.class, Sword.class, Mace.class"),
                () -> r.newContent());
    }

    @Test
    void emptyArrayInsertsSingleEntry() {
        String gen = "static {\n  Category.SCROLL.classes = new Class<?>[]{};\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "SCROLL", "ScrollOfMagicMapping");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("{\n    ScrollOfMagicMapping.class\n  };"),
                () -> r.newContent());
    }
}
