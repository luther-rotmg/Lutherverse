package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    /** A fully-wired mob fixture: sprite chain present, both localization keys, in Bestiary. */
    private static File wiredMobRepo(Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n"
                        + "    RAT(\"sprites/mobs/rat.png\"),\n}");
        write(root.resolve("core/src/main/assets/sprites/mobs/rat.png"), "png");
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/RatSprite.java"),
                "class RatSprite { { texture(GeneralAsset.RAT); } }");
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=rat\nactors.mobs.rat.desc=nasty\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Bestiary.kt"),
                "if (cl == Rat::class.java) {}");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"), "class Generator {}");
        return root.toFile();
    }

    private static EntityGraph.Entity mob(String name, String sprite, String image) {
        return new EntityGraph.Entity("Mob",
                new ContentClass(name, "com.qsr.customspd.actors.mobs", "Mob", false, sprite, image),
                sprite, image);
    }

    /** A fully-wired item fixture: GeneralAsset entry + PNG, both localization keys, in Generator. */
    private static File wiredItemRepo(Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n"
                        + "    SUPPLY_RATION(\"sprites/items/supply_ration.png\"),\n}");
        write(root.resolve("core/src/main/assets/sprites/items/supply_ration.png"), "png");
        write(root.resolve("core/src/main/assets/messages/items.properties"),
                "items.food.supplyration.name=supply ration\nitems.food.supplyration.desc=field ration\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                "class Generator { Class c = SupplyRation.class; }");
        return root.toFile();
    }

    private static EntityGraph.Entity item(String name, String pkg, String superName, String image) {
        return new EntityGraph.Entity("Item",
                new ContentClass(name, pkg, superName, false, null, image),
                null, image);
    }

    @Test
    void wiredMobHasNoFindings(@TempDir Path root) throws Exception {
        File repo = wiredMobRepo(root);
        List<Finding> f = Checks.run(mob("Rat", "RatSprite", null),
                SpriteIndex.load(repo), MessageIndex.load(repo), RegistryIndex.load(repo));
        assertTrue(f.isEmpty(), () -> "expected no findings, got " + f);
    }

    @Test
    void mobMissingEverythingReportsM1M2M3(@TempDir Path root) throws Exception {
        File repo = wiredMobRepo(root);
        // "Ghost": no sprite asset named GHOST, no localization, not in Bestiary
        List<Finding> f = Checks.run(mob("Ghost", "GhostSprite", null),
                SpriteIndex.load(repo), MessageIndex.load(repo), RegistryIndex.load(repo));
        List<String> ids = f.stream().map(Finding::key).collect(Collectors.toList());
        assertTrue(ids.contains("Mob Ghost#M1"));
        assertTrue(ids.contains("Mob Ghost#M2"));
        assertTrue(ids.contains("Mob Ghost#M3"));
        assertEquals(3, f.size());
    }

    @Test
    void wiredItemHasNoFindings(@TempDir Path root) throws Exception {
        File repo = wiredItemRepo(root);
        List<Finding> f = Checks.run(
                item("SupplyRation", "com.qsr.customspd.items.food", "Food", "SUPPLY_RATION"),
                SpriteIndex.load(repo), MessageIndex.load(repo), RegistryIndex.load(repo));
        assertTrue(f.isEmpty(), () -> "expected no findings, got " + f);
    }

    @Test
    void itemMissingEverythingReportsI1I2I3(@TempDir Path root) throws Exception {
        File repo = wiredItemRepo(root);
        // "MysteryBox": no sprite asset named MYSTERY_BOX, no localization, not in Generator
        List<Finding> f = Checks.run(
                item("MysteryBox", "com.qsr.customspd.items.food", "Food", "MYSTERY_BOX"),
                SpriteIndex.load(repo), MessageIndex.load(repo), RegistryIndex.load(repo));
        List<String> ids = f.stream().map(Finding::key).collect(Collectors.toList());
        assertTrue(ids.contains("Item MysteryBox#I1"));
        assertTrue(ids.contains("Item MysteryBox#I2"));
        assertTrue(ids.contains("Item MysteryBox#I3"));
        assertEquals(3, f.size());
    }
}
