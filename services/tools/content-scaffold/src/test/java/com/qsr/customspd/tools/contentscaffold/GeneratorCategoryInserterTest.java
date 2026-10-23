package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GeneratorCategoryInserterTest {
    private static final String GEN =
            "static {\n  Category.FOOD.classes = new Class<?>[]{\n    Food.class,\n    Pasty.class\n  };\n"
          + "  Category.FOOD.defaultProbs = new float[]{ 4, 1 };\n}\n";

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
                + "    SuperBerry.class\n  };\n  Category.FOOD.defaultProbs = new float[]{ 4, 1 };\n}\n";
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
        String gen = "static {\n  FOOD.classes = new Class<?>[]{\n    Food.class,\n    Pasty.class\n  };\n"
                + "  FOOD.defaultProbs = new float[]{ 4, 1 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "FOOD", "Berry");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("Pasty.class,\n    Berry.class"), () -> r.newContent());
    }

    @Test
    void compoundNameSelectorDoesNotFalseMatchAsBareCategory() {
        // "SUPER_FOOD.classes = ..." must NOT be matched by the unqualified "FOOD"
        // lookup -- without a left-boundary check, the optional "(?:Category\.)?"
        // matches zero-width and "FOOD\.classes" matches the tail of
        // "SUPER_FOOD.classes", silently inserting Berry into the wrong category.
        String gen = "static {\n  SUPER_FOOD.classes = new Class<?>[]{\n    Existing.class\n  };\n}\n";
        assertThrows(IllegalArgumentException.class, () -> GeneratorCategoryInserter.addItem(gen, "FOOD", "Berry"));
    }

    @Test
    void unknownCategoryThrows() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorCategoryInserter.addItem(GEN, "WAND", "Berry"));
    }

    @Test
    void singleLineArrayKeepsExistingEntries() {
        String gen = "static {\n  Category.WEP_T2.classes = new Class<?>[]{ Dagger.class, Sword.class };\n"
                + "  Category.WEP_T2.defaultProbs = new float[]{ 2, 2 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "WEP_T2", "Mace");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("Dagger.class, Sword.class, Mace.class"),
                () -> r.newContent());
    }

    /** Shaped like the real STONE block: trailing comments on entries, deck probs cloned. */
    private static final String STONE_GEN =
            "static {\n"
          + "  STONE.classes = new Class<?>[]{\n"
          + "      StoneOfEnchantment.class,   //1 is guaranteed to drop on floors 6-19\n"
          + "      StoneOfAugmentation.class   //1 is sold in each shop\n"
          + "  };\n"
          + "  STONE.defaultProbs = new float[]{ 0, 5 };\n"
          + "  STONE.probs = STONE.defaultProbs.clone();\n"
          + "}\n";

    /** Number of Foo.class elements in the named array literal (comments ignored). */
    private static int classCount(String gen, String field) {
        int open = gen.indexOf(field + " = new Class<?>[]{");
        assertTrue(open >= 0, () -> field + " not found in " + gen);
        String body = gen.substring(open, gen.indexOf('}', open)).replaceAll("//[^\n]*", "");
        Matcher m = Pattern.compile("\\.class\\b").matcher(body);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /** Number of elements in the named float[] literal. */
    private static int probCount(String gen, String field) {
        int open = gen.indexOf(field + " = new float[]{");
        assertTrue(open >= 0, () -> field + " not found in " + gen);
        int start = gen.indexOf('{', open) + 1;
        String body = gen.substring(start, gen.indexOf('}', start)).replaceAll("//[^\n]*", "").trim();
        return body.isEmpty() ? 0 : body.split(",").length;
    }

    @Test
    void stoneInsertExtendsClassesAndDefaultProbsByExactlyOne() {
        int classesBefore = classCount(STONE_GEN, "STONE.classes");
        int probsBefore = probCount(STONE_GEN, "STONE.defaultProbs");
        assertEquals(classesBefore, probsBefore, "fixture must start in lockstep");
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(STONE_GEN, "STONE", "InsightCrystal");
        assertTrue(r.inserted());
        assertEquals(classesBefore + 1, classCount(r.newContent(), "STONE.classes"), () -> r.newContent());
        assertEquals(probsBefore + 1, probCount(r.newContent(), "STONE.defaultProbs"), () -> r.newContent());
        assertTrue(r.newContent().contains("STONE.defaultProbs = new float[]{ 0, 5, 1 };"), () -> r.newContent());
        // the clone line is derived at runtime and must be left alone
        assertTrue(r.newContent().contains("STONE.probs = STONE.defaultProbs.clone();"), () -> r.newContent());
    }

    @Test
    void trailingCommentStaysWithItsOriginalEntry() {
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(STONE_GEN, "STONE", "InsightCrystal");
        assertTrue(r.newContent().contains(
                "StoneOfAugmentation.class,   //1 is sold in each shop\n      InsightCrystal.class\n  };"),
                () -> r.newContent());
        assertFalse(r.newContent().contains("InsightCrystal.class //"), () -> r.newContent());
    }

    @Test
    void reRunAfterProbsExtensionIsANoOp() {
        AnchorInserter.Result first = GeneratorCategoryInserter.addItem(STONE_GEN, "STONE", "InsightCrystal");
        AnchorInserter.Result second = GeneratorCategoryInserter.addItem(first.newContent(), "STONE", "InsightCrystal");
        assertFalse(second.inserted());
        assertEquals(first.newContent(), second.newContent());
    }

    @Test
    void categoryWithOnlyAProbsLiteralStaysInLockstep() {
        // GOLD's real shape: no defaultProbs (no deck), probs is the literal itself.
        String gen = "static {\n  GOLD.classes = new Class<?>[]{\n      Gold.class };\n"
                + "  GOLD.probs = new float[]{ 1 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "GOLD", "Nugget");
        assertTrue(r.inserted());
        assertEquals(2, classCount(r.newContent(), "GOLD.classes"), () -> r.newContent());
        assertEquals(2, probCount(r.newContent(), "GOLD.probs"), () -> r.newContent());
    }

    @Test
    void siblingDefaultProbsAndProbsLiteralsExtendTogether() {
        String gen = "static {\n  RING.classes = new Class<?>[]{ A.class, B.class };\n"
                + "  RING.defaultProbs = new float[]{ 3, 3 };\n"
                + "  RING.probs = new float[]{ 3, 3 };\n"
                + "  WEP_T3.probs = RING.defaultProbs.clone();\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "RING", "C");
        assertTrue(r.inserted());
        assertEquals(3, classCount(r.newContent(), "RING.classes"), () -> r.newContent());
        assertEquals(3, probCount(r.newContent(), "RING.defaultProbs"), () -> r.newContent());
        assertEquals(3, probCount(r.newContent(), "RING.probs"), () -> r.newContent());
    }

    @Test
    void customWeightIsAppendedToTheProbs() {
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(STONE_GEN, "STONE", "InsightCrystal", "2.5");
        assertTrue(r.newContent().contains("STONE.defaultProbs = new float[]{ 0, 5, 2.5 };"), () -> r.newContent());
    }

    @Test
    void nonNumericWeightThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratorCategoryInserter.addItem(STONE_GEN, "STONE", "InsightCrystal", "heavy"));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratorCategoryInserter.addItem(STONE_GEN, "STONE", "InsightCrystal", "-1"));
    }

    @Test
    void categoryWithoutAnyProbsLiteralThrows() {
        String gen = "static {\n  STONE.classes = new Class<?>[]{ A.class };\n"
                + "  STONE.probs = STONE.defaultProbs.clone();\n}\n";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GeneratorCategoryInserter.addItem(gen, "STONE", "B"));
        assertTrue(e.getMessage().contains("STONE"), e.getMessage());
    }

    @Test
    void alreadyDesyncedCategoryThrowsInsteadOfCompoundingIt() {
        String gen = "static {\n  STONE.classes = new Class<?>[]{ A.class, B.class };\n"
                + "  STONE.defaultProbs = new float[]{ 5 };\n}\n";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GeneratorCategoryInserter.addItem(gen, "STONE", "C"));
        assertTrue(e.getMessage().contains("2 entries") && e.getMessage().contains("has 1"), e.getMessage());
    }

    @Test
    void dispatchCategoriesThrow() {
        // The real file declares WEAPON/MISSILE with empty classes and ARMOR with a probs literal;
        // Generator.random() special-cases all three, so a "wired" item would never drop.
        String gen = "static {\n  WEAPON.classes = new Class<?>[]{};\n  WEAPON.probs = new float[]{};\n"
                + "  ARMOR.classes = new Class<?>[]{ ClothArmor.class };\n  ARMOR.probs = new float[]{ 1 };\n}\n";
        assertThrows(IllegalArgumentException.class, () -> GeneratorCategoryInserter.addItem(gen, "WEAPON", "X"));
        assertThrows(IllegalArgumentException.class, () -> GeneratorCategoryInserter.addItem(gen, "ARMOR", "X"));
    }

    @Test
    void otherCategorysProbsAreNotTouched() {
        String gen = "static {\n  WEP_T1.classes = new Class<?>[]{ A.class };\n"
                + "  WEP_T1.defaultProbs = new float[]{ 2 };\n"
                + "  WEP_T3.probs = WEP_T1.defaultProbs.clone();\n"
                + "  SUPER_WEP_T1.defaultProbs = new float[]{ 9 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "WEP_T1", "B");
        assertTrue(r.newContent().contains("WEP_T1.defaultProbs = new float[]{ 2, 1 };"), () -> r.newContent());
        assertTrue(r.newContent().contains("SUPER_WEP_T1.defaultProbs = new float[]{ 9 };"), () -> r.newContent());
    }

    @Test
    void classMentionedInACommentIsNeitherPresentNorTheInsertionPoint() {
        String gen = "static {\n  FOOD.classes = new Class<?>[]{\n    Food.class //see Berry.class\n  };\n"
                + "  FOOD.defaultProbs = new float[]{ 4 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "FOOD", "Berry");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("Food.class, //see Berry.class\n    Berry.class\n  };"),
                () -> r.newContent());
    }

    @Test
    void trailingCommaWithCommentIsPreserved() {
        String gen = "static {\n  FOOD.classes = new Class<?>[]{\n    Food.class, //staple\n  };\n"
                + "  FOOD.defaultProbs = new float[]{ 4 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "FOOD", "Berry");
        assertTrue(r.newContent().contains("Food.class, //staple\n    Berry.class,\n  };"), () -> r.newContent());
    }

    @Test
    void emptyArrayInsertsSingleEntry() {
        String gen = "static {\n  Category.SCROLL.classes = new Class<?>[]{};\n"
                + "  Category.SCROLL.defaultProbs = new float[]{};\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "SCROLL", "ScrollOfMagicMapping");
        assertTrue(r.inserted());
        assertTrue(r.newContent().contains("{\n    ScrollOfMagicMapping.class\n  };"),
                () -> r.newContent());
    }

    @Test
    void packedMultiLineProbsLiteralStaysInLockstep() {
        // Several numbers per line: the new line's indent must come from the line's whitespace,
        // not from everything before the last number.
        String gen = "static {\n  FOOD.classes = new Class<?>[]{\n    A.class,\n    B.class\n  };\n"
                + "  FOOD.defaultProbs = new float[]{\n    4, 1\n  };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "FOOD", "Zzz");
        assertTrue(r.inserted());
        assertEquals(3, classCount(r.newContent(), "FOOD.classes"), () -> r.newContent());
        assertEquals(3, probCount(r.newContent(), "FOOD.defaultProbs"), () -> r.newContent());
        assertTrue(r.newContent().contains("{\n    4, 1,\n    1\n  };"), () -> r.newContent());
    }

    @Test
    void packedMultiLineClassesLiteralStaysInLockstep() {
        String gen = "static {\n  FOOD.classes = new Class<?>[]{\n    A.class, B.class,\n    C.class, D.class\n  };\n"
                + "  FOOD.defaultProbs = new float[]{ 4, 1, 1, 1 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "FOOD", "Zzz");
        assertTrue(r.inserted());
        assertEquals(5, classCount(r.newContent(), "FOOD.classes"), () -> r.newContent());
        assertEquals(5, probCount(r.newContent(), "FOOD.defaultProbs"), () -> r.newContent());
        assertTrue(r.newContent().contains("C.class, D.class,\n    Zzz.class\n  };"), () -> r.newContent());
    }

    @Test
    void defaultProbs2SiblingExtendsWithDefaultProbs() {
        // upstream v2.5.4 gives POTION/SCROLL a second weight array that must stay the same length
        String gen = "static {\n  POTION.classes = new Class<?>[]{ A.class, B.class };\n"
                + "  POTION.defaultProbs = new float[]{ 3, 3 };\n"
                + "  POTION.defaultProbs2 = new float[]{ 0, 1 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "POTION", "C");
        assertTrue(r.inserted());
        assertEquals(3, probCount(r.newContent(), "POTION.defaultProbs"), () -> r.newContent());
        assertEquals(3, probCount(r.newContent(), "POTION.defaultProbs2"), () -> r.newContent());
    }

    @Test
    void commentedOutProbLiteralIsNotTreatedAsLive() {
        String gen = "static {\n  FOOD.classes = new Class<?>[]{ A.class };\n"
                + "  //FOOD.defaultProbs = new float[]{ 9, 9, 9 };\n"
                + "  FOOD.defaultProbs = new float[]{ 4 };\n}\n";
        AnchorInserter.Result r = GeneratorCategoryInserter.addItem(gen, "FOOD", "B");
        assertTrue(r.newContent().contains("//FOOD.defaultProbs = new float[]{ 9, 9, 9 };"), () -> r.newContent());
        assertTrue(r.newContent().contains("FOOD.defaultProbs = new float[]{ 4, 1 };"), () -> r.newContent());
    }

    @Test
    void weightWithALeadingZeroThrows() {
        // 08 / 09 are invalid octal int literals in the generated Java
        assertThrows(IllegalArgumentException.class,
                () -> GeneratorCategoryInserter.addItem(STONE_GEN, "STONE", "InsightCrystal", "08"));
    }
}
