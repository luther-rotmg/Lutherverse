package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentScaffoldCliItemTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    @Test
    void generatesAllItemTouchpoints(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n    ANKH(\"sprites/items/ankh.png\"),\n"
              + "    // @content-scaffold:items\n}\n");
        write(root.resolve("core/src/main/assets/messages/items/items.properties"),
                "items.ankh.name=ankh\n### @content-scaffold:items\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                "class Generator {\n  static {\n    Category.FOOD.classes = new Class<?>[]{\n      Pasty.class\n    };\n  }\n}\n");
        File r = root.toFile();
        ContentScaffoldCli.generateItem(r, "Berry", "FOOD", "1");
        assertTrue(Files.exists(root.resolve("core/src/main/java/com/qsr/customspd/items/Berry.java")));
        assertTrue(Files.exists(root.resolve("core/src/main/assets/sprites/items/berry.png")));
        assertTrue(Files.readString(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"))
                .contains("BERRY(\"sprites/items/berry.png\"),"));
        assertTrue(Files.readString(root.resolve("core/src/main/assets/messages/items/items.properties"))
                .contains("items.berry.name="));
        assertTrue(Files.readString(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"))
                .contains("Berry.class"));
    }

    @Test
    void idempotentReRunSkips(@TempDir Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n    ANKH(\"sprites/items/ankh.png\"),\n"
              + "    // @content-scaffold:items\n}\n");
        write(root.resolve("core/src/main/assets/messages/items/items.properties"),
                "items.ankh.name=ankh\n### @content-scaffold:items\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                "class Generator {\n  static {\n    Category.FOOD.classes = new Class<?>[]{\n      Pasty.class\n    };\n  }\n}\n");
        File r = root.toFile();
        ContentScaffoldCli.generateItem(r, "Berry", "FOOD", "1");
        ContentScaffoldCli.GenResult second = ContentScaffoldCli.generateItem(r, "Berry", "FOOD", "1");
        // class file, PNG, GeneralAsset, properties, and Generator insertions are all
        // no-ops on the second run.
        assertTrue(second.skipped().size() >= 5, () -> "expected >=5 skipped, got " + second.skipped());
        assertTrue(second.created().isEmpty(), () -> "expected nothing created, got " + second.created());
        assertTrue(second.modified().isEmpty(), () -> "expected nothing modified, got " + second.modified());
    }

    @Test
    void generalAssetIdempotencyDoesNotSuffixCollide(@TempDir Path root) throws Exception {
        // A pre-existing "SUPER_BERRY(" entry contains "BERRY(" as a substring. If the
        // idempotency token passed to AnchorInserter.insertAbove were the bare
        // "BERRY(" (as Task 9's draft brief had it) rather than the full generated
        // line, this would false-positive against SUPER_BERRY( via plain String.contains
        // and silently skip inserting the real BERRY( entry. Mirrors Task 8's mob-side
        // GREATER_WISP regression test.
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n    ANKH(\"sprites/items/ankh.png\"),\n"
              + "    SUPER_BERRY(\"sprites/items/super_berry.png\"),\n"
              + "    // @content-scaffold:items\n}\n");
        write(root.resolve("core/src/main/assets/messages/items/items.properties"),
                "items.ankh.name=ankh\n### @content-scaffold:items\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"),
                "class Generator {\n  static {\n    Category.FOOD.classes = new Class<?>[]{\n      Pasty.class\n    };\n  }\n}\n");
        File r = root.toFile();
        ContentScaffoldCli.generateItem(r, "Berry", "FOOD", "1");
        String content = Files.readString(
                root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"));
        assertTrue(content.contains("    BERRY(\"sprites/items/berry.png\"),"), () -> content);
    }
}
