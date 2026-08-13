package com.qsr.customspd.tools.contentscaffold;

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
}
