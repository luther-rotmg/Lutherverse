package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentScaffoldCliMobTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    private static File repo(Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n    RAT(\"sprites/chars/rat.png\"),\n"
              + "    // @content-scaffold:mobs\n    BOMB(\"sprites/items/bomb.png\"),\n}\n");
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=rat\n### @content-scaffold:mobs\n");
        write(root.resolve("core/src/main/assets/dungeon/dungeon.json"),
                "{\n  \"dungeon\": {\n    \"3\": {\n      \"depth\": 3,\n      \"bestiary\": [\n"
              + "        \"Gnoll\"\n      ]\n    }\n  }\n}\n");
        return root.toFile();
    }

    @Test
    void generatesAllMobTouchpoints(@TempDir Path root) throws Exception {
        File r = repo(root);
        ContentScaffoldCli.generateMob(r, "Wisp", 3);
        assertTrue(Files.exists(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Wisp.java")));
        assertTrue(Files.exists(root.resolve("core/src/main/java/com/qsr/customspd/sprites/WispSprite.java")));
        assertTrue(Files.exists(root.resolve("core/src/main/assets/sprites/chars/wisp.png")));
        assertTrue(Files.readString(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"))
                .contains("WISP(\"sprites/chars/wisp.png\"),"));
        assertTrue(Files.readString(root.resolve("core/src/main/assets/messages/actors/actors.properties"))
                .contains("actors.mobs.wisp.name="));
        assertTrue(Files.readString(root.resolve("core/src/main/assets/dungeon/dungeon.json"))
                .contains("\"Wisp\""));
    }

    @Test
    void idempotentReRunSkips(@TempDir Path root) throws Exception {
        File r = repo(root);
        ContentScaffoldCli.generateMob(r, "Wisp", 3);
        ContentScaffoldCli.GenResult second = ContentScaffoldCli.generateMob(r, "Wisp", 3);
        // GeneralAsset/properties/dungeon insertions are all no-ops on the second run
        assertTrue(second.skipped().size() >= 3, () -> "expected >=3 skipped, got " + second.skipped());
    }

    @Test
    void generalAssetIdempotencyDoesNotSuffixCollide(@TempDir Path root) throws Exception {
        File r = repo(root);
        Path asset = root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt");
        // A pre-existing "GREATER_WISP(" entry contains "WISP(" as a substring. A naive
        // idempotency token of just "WISP(" would false-positive against it and silently
        // skip the insertion, so this must still insert the real WISP( line.
        write(asset,
                "enum class GeneralAsset(val path: String) {\n    RAT(\"sprites/chars/rat.png\"),\n"
              + "    GREATER_WISP(\"sprites/chars/greater_wisp.png\"),\n"
              + "    // @content-scaffold:mobs\n    BOMB(\"sprites/items/bomb.png\"),\n}\n");
        ContentScaffoldCli.generateMob(r, "Wisp", 3);
        String content = Files.readString(asset);
        assertTrue(content.contains("    WISP(\"sprites/chars/wisp.png\"),"), () -> content);
    }

    @Test
    void longerSiblingKeyDoesNotFalsePositiveTheLocalizationToken(@TempDir Path root) throws Exception {
        // A pre-existing "actors.mobs.wisps.wisp.elite.name=" key contains "actors.mobs.wisp"
        // as a literal substring (since "wisp" is a prefix of "wisps"). The idempotency token
        // AnchorInserter.insertAbove uses for the localization touchpoint is already the
        // delimited "actors.mobs.wisp.name=" (not a bare "actors.mobs.wisp" prefix), so this
        // must NOT false-positive via plain String.contains and must still insert the real
        // actors.mobs.wisp.name= entry.
        File r = repo(root);
        Path props = root.resolve("core/src/main/assets/messages/actors/actors.properties");
        write(props, "actors.mobs.rat.name=rat\nactors.mobs.wisps.wisp.elite.name=Elite Wisp\n"
                + "### @content-scaffold:mobs\n");
        ContentScaffoldCli.GenResult result = ContentScaffoldCli.generateMob(r, "Wisp", 3);
        String content = Files.readString(props);
        assertTrue(content.contains("actors.mobs.wisp.name="), () -> content);
        assertTrue(result.modified().contains("actors.mobs.wisp"), () -> result.modified().toString());
    }

    @Test
    void invalidDepthLeavesNoPartialWrites(@TempDir Path root) throws Exception {
        File r = repo(root);
        Path generalAsset = root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt");
        Path props = root.resolve("core/src/main/assets/messages/actors/actors.properties");
        Path dungeon = root.resolve("core/src/main/assets/dungeon/dungeon.json");
        String generalAssetBefore = Files.readString(generalAsset);
        String propsBefore = Files.readString(props);
        String dungeonBefore = Files.readString(dungeon);

        assertThrows(IllegalArgumentException.class, () -> ContentScaffoldCli.generateMob(r, "Wisp", 999));

        assertFalse(Files.exists(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Wisp.java")));
        assertFalse(Files.exists(root.resolve("core/src/main/java/com/qsr/customspd/sprites/WispSprite.java")));
        assertFalse(Files.exists(root.resolve("core/src/main/assets/sprites/chars/wisp.png")));
        assertEquals(generalAssetBefore, Files.readString(generalAsset));
        assertEquals(propsBefore, Files.readString(props));
        assertEquals(dungeonBefore, Files.readString(dungeon));
    }
}
