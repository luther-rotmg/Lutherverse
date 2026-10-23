package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EntityGraphTest {
    private static ContentClass cc(String name, String pkg, String sup, boolean abs,
                                   String sprite, String image) {
        return new ContentClass(name, pkg, sup, abs, sprite, image);
    }

    @Test
    void enumeratesConcreteMobsAndResolvesInheritedSprite() {
        List<ContentClass> all = List.of(
                cc("Mob", "com.qsr.customspd.actors.mobs", "Char", true, null, null),
                cc("Rat", "com.qsr.customspd.actors.mobs", "Mob", false, "RatSprite", null),
                cc("Albino", "com.qsr.customspd.actors.mobs", "Rat", false, null, null));
        List<EntityGraph.Entity> es = EntityGraph.build(all);
        assertEquals(2, es.size()); // Mob is abstract+base, excluded
        EntityGraph.Entity albino = es.stream()
                .filter(e -> e.cls().simpleName().equals("Albino")).findFirst().orElseThrow();
        assertEquals("Mob", albino.kind());
        assertEquals("RatSprite", albino.resolvedSpriteClass()); // inherited from Rat
    }

    @Test
    void enumeratesItemsAndResolvesInheritedImage() {
        List<ContentClass> all = List.of(
                cc("Item", "com.qsr.customspd.items", "Object", true, null, null),
                cc("Food", "com.qsr.customspd.items.food", "Item", true, null, null),
                cc("SupplyRation", "com.qsr.customspd.items.food", "Food", false, null, "SUPPLY_RATION"));
        List<EntityGraph.Entity> es = EntityGraph.build(all);
        assertEquals(1, es.size()); // Item, Food are abstract/base
        assertEquals("Item", es.get(0).kind());
        assertEquals("SUPPLY_RATION", es.get(0).resolvedImageAsset());
    }

    @Test
    void excludesClassesWhoseAncestryNeverReachesMobOrItem() {
        List<ContentClass> all = List.of(
                cc("Helper", "com.qsr.customspd.utils", "Object", false, null, null));
        assertTrue(EntityGraph.build(all).isEmpty());
    }

    @Test
    void guardsAgainstDuplicateSimpleNamesInAncestryChain() {
        // Two unrelated classes named "Helper" live in different packages, both
        // extending Mob directly with different sprites.
        List<ContentClass> all = List.of(
                cc("Mob", "com.qsr.customspd.actors.mobs", "Char", true, null, null),
                cc("Helper", "com.qsr.customspd.actors.mobs.variant1", "Mob", false, "VariantASprite", null),
                cc("Helper", "com.qsr.customspd.actors.mobs.variant2", "Mob", false, "VariantBSprite", null),
                cc("Elf", "com.qsr.customspd.actors.mobs", "Helper", false, null, null));
        List<EntityGraph.Entity> es = EntityGraph.build(all);
        // Elf's ancestry passes through the ambiguous "Helper" name, which could mean
        // either variant; it must not be silently resolved to whichever one happened
        // to win the name collision.
        assertTrue(es.stream().noneMatch(e -> e.cls().simpleName().equals("Elf")),
                () -> "Elf's ancestry is ambiguous and must be excluded, not guessed: " + es);
        // Both Helper classes are still audited on their own terms (their own
        // ancestry to Mob is unambiguous).
        assertEquals(2, es.stream().filter(e -> e.cls().simpleName().equals("Helper")).count());
    }

    @Test
    void reportsTheAmbiguousNameAndWhatItExcludes() {
        // Excluding Elf above is only safe if it is visible: the ambiguity must be
        // reported with both declarations and the class it drops from the audit.
        List<ContentClass> all = List.of(
                cc("Mob", "com.qsr.customspd.actors.mobs", "Char", true, null, null),
                cc("Helper", "com.qsr.customspd.actors.mobs.variant1", "Mob", false, "VariantASprite", null),
                cc("Helper", "com.qsr.customspd.actors.mobs.variant2", "Mob", false, "VariantBSprite", null),
                cc("Elf", "com.qsr.customspd.actors.mobs", "Helper", false, null, null),
                cc("Rat", "com.qsr.customspd.actors.mobs", "Mob", false, "RatSprite", null));
        List<EntityGraph.Ambiguity> amb = EntityGraph.ambiguities(all);
        assertEquals(List.of(new EntityGraph.Ambiguity("Helper",
                        List.of("com.qsr.customspd.actors.mobs.variant1.Helper",
                                "com.qsr.customspd.actors.mobs.variant2.Helper"),
                        List.of("com.qsr.customspd.actors.mobs.Elf"))),
                amb);
    }

    @Test
    void reportsNoAmbiguityWhenSimpleNamesAreUnique() {
        List<ContentClass> all = List.of(
                cc("Mob", "com.qsr.customspd.actors.mobs", "Char", true, null, null),
                cc("Rat", "com.qsr.customspd.actors.mobs", "Mob", false, "RatSprite", null));
        assertTrue(EntityGraph.ambiguities(all).isEmpty());
    }
}
