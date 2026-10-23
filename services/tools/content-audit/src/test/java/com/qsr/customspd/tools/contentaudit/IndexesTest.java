package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexesTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    @Test
    void spriteIndexResolvesAssetPathToPng(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n"
                        + "    RAT(\"sprites/mobs/rat.png\"),\n"
                        + "    GHOST(\"sprites/mobs/ghost.png\"),\n}");
        write(root.resolve("core/src/main/assets/sprites/mobs/rat.png"), "png");
        // A sprite class textures with GeneralAsset.RAT; lets a mob resolve via spriteClass.
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/RatSprite.java"),
                "class RatSprite { { texture(GeneralAsset.RAT); } }");
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/GhostSprite.java"),
                "class GhostSprite { { texture(GeneralAsset.GHOST); } }");
        SpriteIndex s = SpriteIndex.load(root.toFile());
        assertTrue(s.spriteExists("RAT"));
        assertFalse(s.spriteExists("GHOST")); // declared but file missing
        assertFalse(s.spriteExists("NONEXISTENT"));
        assertTrue(s.mobSpriteExists("RatSprite"));    // RatSprite -> RAT -> rat.png exists
        assertFalse(s.mobSpriteExists("GhostSprite"));  // GhostSprite -> GHOST -> file missing
        assertFalse(s.mobSpriteExists("NoSuchSprite"));
    }

    @Test
    void spriteIndexResolvesNestedSpriteClasses(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n"
                        + "    MIMIC(\"sprites/mobs/mimic.png\"),\n"
                        + "    OWN(\"sprites/mobs/own.png\"),\n"
                        + "    GONE(\"sprites/mobs/gone.png\"),\n}");
        write(root.resolve("core/src/main/assets/sprites/mobs/mimic.png"), "png");
        write(root.resolve("core/src/main/assets/sprites/mobs/own.png"), "png");
        // gone.png deliberately absent
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/MimicSprite.java"),
                "public class MimicSprite extends MobSprite {\n"
                        + "  public MimicSprite() { texture(GeneralAsset.MIMIC); }\n"
                        + "  public static class Golden extends MimicSprite {}\n"          // inherits the outer asset
                        + "  public static class Crystal extends MimicSprite {}\n"
                        + "  public static class Own extends MobSprite { Own() { texture(GeneralAsset.OWN); } }\n"
                        + "  public static class Lost extends MobSprite { Lost() { texture(GeneralAsset.GONE); } }\n"
                        + "  public static class Orphan extends NoSuchSprite {}\n"          // no asset anywhere
                        + "}");
        SpriteIndex s = SpriteIndex.load(root.toFile());
        assertTrue(s.mobSpriteExists("MimicSprite"));
        assertTrue(s.mobSpriteExists("MimicSprite.Golden"));   // via MimicSprite -> MIMIC
        assertTrue(s.mobSpriteExists("MimicSprite.Crystal"));
        assertTrue(s.mobSpriteExists("MimicSprite.Own"));      // its own texture ref
        assertFalse(s.mobSpriteExists("MimicSprite.Lost"));    // asset declared, PNG missing
        assertFalse(s.mobSpriteExists("MimicSprite.Orphan"));  // nothing to resolve to
        assertFalse(s.mobSpriteExists("MimicSprite.NoSuch"));
        assertFalse(s.mobSpriteExists("Golden"));              // nested names are qualified by their outer class
    }

    @Test
    void messageIndexFindsKeysAcrossPropertiesFiles(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=marsupial rat\nactors.mobs.rat.desc=nasty\n");
        MessageIndex m = MessageIndex.load(root.toFile());
        assertTrue(m.hasKey("actors.mobs.rat.name"));
        assertTrue(m.hasKey("actors.mobs.rat.desc"));
        assertFalse(m.hasKey("actors.mobs.ghost.name"));
    }

    @Test
    void messageIndexIgnoresTranslationsSoTheyCannotMaskAMissingEnglishKey(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=rat\n");
        write(root.resolve("core/src/main/assets/messages/actors/actors_ru.properties"),
                "actors.mobs.rat.name=krysa\nactors.mobs.rat.desc=zlaya\n");
        write(root.resolve("core/src/main/assets/messages/actors/actors_pt_BR.properties"),
                "actors.mobs.rat.desc=ruim\n");
        MessageIndex m = MessageIndex.load(root.toFile());
        assertTrue(m.hasKey("actors.mobs.rat.name"));
        assertFalse(m.hasKey("actors.mobs.rat.desc")); // only translated, English players would see the raw key
    }

    @Test
    void registryIndexDetectsWordTokenReferences(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Bestiary.kt"),
                "fun mobClass() { if (cl == Rat::class.java) {} ; val x = RatKing }");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                "class Generator { Category c = new Category(Ration.class); }");
        RegistryIndex r = RegistryIndex.load(root.toFile());
        assertTrue(r.bestiaryReferences("Rat"));
        assertFalse(r.bestiaryReferences("Ration")); // substring of nothing; word-bounded
        assertTrue(r.generatorReferences("Ration"));
        assertFalse(r.bestiaryReferences("King")); // 'King' only appears embedded in 'RatKing', not as a whole word
    }

    @Test
    void registryIndexCountsDungeonJsonSpawnListsAsRegistration(@TempDir Path root) throws Exception {
        // CPDU's Bestiary.kt resolves each level's "bestiary"/"rare_mob" strings by
        // reflection, and RegularLevel places "extra_mobs"/"extra_items" by "type";
        // none of those names appear as tokens in Bestiary.kt or Generator.java.
        write(root.resolve("core/src/main/assets/dungeon/dungeon.json"),
                "{ \"dungeon\": {\n"
                        + "  \"1\": { \"depth\": 1, \"type\": \"regular\", \"layout\": \"SewerBossLevel\",\n"
                        + "    \"bestiary\": [ \"Rat\", \"Snake\" ], \"rare_mob\": \"Thief\",\n"
                        + "    \"extra_mobs\": [ { \"type\": \"npcs.Sheep\", \"hp\": 5 } ],\n"
                        + "    \"extra_items\": [ { \"type\": \"weapon.missiles.darts.TippedDart\", \"quantity\": 2 } ] }\n"
                        + "}, \"rat_king_level\": \"1\" }");
        RegistryIndex r = RegistryIndex.load(root.toFile());
        assertTrue(r.bestiaryReferences("Snake"));
        assertTrue(r.bestiaryReferences("Thief"));
        assertTrue(r.bestiaryReferences("Sheep")); // package-relative type -> simple name
        assertTrue(r.generatorReferences("TippedDart"));
        // Names outside the spawn lists must not count: a level "type"/"layout" value,
        // a top-level quest key, a mob absent from the file, or a mob name as an item.
        assertFalse(r.bestiaryReferences("regular"));
        assertFalse(r.bestiaryReferences("SewerBossLevel"));
        assertFalse(r.bestiaryReferences("RatKing"));
        assertFalse(r.bestiaryReferences("Crab"));
        assertFalse(r.generatorReferences("Snake"));
        assertFalse(r.bestiaryReferences("TippedDart"));
    }
}
