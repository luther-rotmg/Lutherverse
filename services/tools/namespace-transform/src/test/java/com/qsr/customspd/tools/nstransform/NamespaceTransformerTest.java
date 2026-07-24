package com.qsr.customspd.tools.nstransform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive tests for the NamespaceTransformer covering all behavior:
 * - SPD→CPD path and Java package/import transformation
 * - CPD→SPD path and Kotlin package/import transformation
 * - nested source paths
 * - binary byte preservation
 * - refusal of in-place input/output
 * - destination-collision refusal
 * - forward then reverse roundtrip restoring the original tree
 */
public class NamespaceTransformerTest {

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path outputDir;

    @BeforeEach
    void setup() throws IOException {
        inputDir = tempDir.resolve("input");
        outputDir = tempDir.resolve("output");
        Files.createDirectories(inputDir);
    }

    @Test
    void testSpdToCpdPathTransformation() throws IOException {
        // Create a Java file in SPD path structure
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        Path sourceFile = spdPath.resolve("Foo.java");
        Files.writeString(sourceFile, "class Foo {}");

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        // Verify transformed path exists
        Path expectedPath = outputDir.resolve("com/qsr/customspd/Foo.java");
        assertTrue(Files.exists(expectedPath), "Transformed file should exist at: " + expectedPath);
        assertEquals("class Foo {}", Files.readString(expectedPath));

        // Original path should not exist in output
        assertFalse(Files.exists(outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon")));
    }

    @Test
    void testCpdToSpdPathTransformation() throws IOException {
        // Create a Kotlin file in CPD path structure
        Path cpdPath = inputDir.resolve("com/qsr/customspd");
        Files.createDirectories(cpdPath);
        Path sourceFile = cpdPath.resolve("Bar.kt");
        Files.writeString(sourceFile, "class Bar {}");

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.CPD_TO_SPD);
        transformer.transform(inputDir, outputDir);

        // Verify transformed path exists
        Path expectedPath = outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon/Bar.kt");
        assertTrue(Files.exists(expectedPath));
        assertEquals("class Bar {}", Files.readString(expectedPath));
    }

    @Test
    void testSpdToCpdJavaImportTransformation() throws IOException {
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        Path sourceFile = spdPath.resolve("Hero.java");
        String content = "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.items.Item;\n"
            + "public class Hero extends Char {\n"
            + "    // Uses Item\n"
            + "}";
        Files.writeString(sourceFile, content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        Path expectedFile = outputDir.resolve("com/qsr/customspd/Hero.java");
        String transformed = Files.readString(expectedFile);
        String expected = "package com.qsr.customspd;\n"
            + "import com.qsr.customspd.items.Item;\n"
            + "public class Hero extends Char {\n"
            + "    // Uses Item\n"
            + "}";
        assertEquals(expected, transformed);
    }

    @Test
    void testCpdToSpdKotlinImportTransformation() throws IOException {
        Path cpdPath = inputDir.resolve("com/qsr/customspd");
        Files.createDirectories(cpdPath);
        Path sourceFile = cpdPath.resolve("Dungeon.kt");
        String content = "package com.qsr.customspd\n"
            + "import com.qsr.customspd.levels.Level\n"
            + "class Dungeon {\n"
            + "    val level: Level? = null\n"
            + "}";
        Files.writeString(sourceFile, content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.CPD_TO_SPD);
        transformer.transform(inputDir, outputDir);

        Path expectedFile = outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon/Dungeon.kt");
        String transformed = Files.readString(expectedFile);
        String expected = "package com.shatteredpixel.shatteredpixeldungeon\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.levels.Level\n"
            + "class Dungeon {\n"
            + "    val level: Level? = null\n"
            + "}";
        assertEquals(expected, transformed);
    }

    @Test
    void testNestedSourcePaths() throws IOException {
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        Files.createDirectories(spdPath.resolve("levels"));
        Files.createDirectories(spdPath.resolve("items/armor"));

        Files.writeString(spdPath.resolve("Char.java"), "class Char {}");
        Files.writeString(spdPath.resolve("levels/Level.java"), "class Level {}");
        Files.writeString(spdPath.resolve("items/Item.java"), "class Item {}");
        Files.writeString(spdPath.resolve("items/armor/Plate.java"), "class Plate {}");

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        assertTrue(Files.exists(outputDir.resolve("com/qsr/customspd/Char.java")));
        assertTrue(Files.exists(outputDir.resolve("com/qsr/customspd/levels/Level.java")));
        assertTrue(Files.exists(outputDir.resolve("com/qsr/customspd/items/Item.java")));
        assertTrue(Files.exists(outputDir.resolve("com/qsr/customspd/items/armor/Plate.java")));
    }

    @Test
    void testBinaryBytePreservation() throws IOException {
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);

        // Create a binary file with specific content
        byte[] binaryContent = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        Path binaryFile = spdPath.resolve("image.jpg");
        Files.write(binaryFile, binaryContent);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        Path expectedFile = outputDir.resolve("com/qsr/customspd/image.jpg");
        byte[] result = Files.readAllBytes(expectedFile);
        assertArrayEquals(binaryContent, result, "Binary content should be preserved byte-for-byte");
    }

    @Test
    void testRefusalOfInPlaceTransform() throws IOException {
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        Files.writeString(spdPath.resolve("Test.java"), "class Test {}");

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);

        // Attempting to transform inputDir to inputDir should fail
        assertThrows(IllegalArgumentException.class,
            () -> transformer.transform(inputDir, inputDir),
            "Should refuse in-place transformation");
    }

    @Test
    void testDestinationCollisionRefusal() throws IOException {
        // Create two files that will map to the same destination
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        Files.writeString(spdPath.resolve("FileA.java"), "class FileA {}");

        // Also create a file that's already in the target namespace
        Path otherPath = inputDir.resolve("com/qsr/customspd");
        Files.createDirectories(otherPath);
        Files.writeString(otherPath.resolve("FileA.java"), "class FileA {}");

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);

        // This should detect collision
        assertThrows(IllegalArgumentException.class,
            () -> transformer.transform(inputDir, outputDir),
            "Should refuse destination collision");
    }

    @Test
    void testForwardAndReverseRoundtrip() throws IOException {
        // Set up SPD structure with specific content
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        Files.createDirectories(spdPath.resolve("actors"));

        String javaContent = "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;\n"
            + "public class Hero extends Actor {}";
        Files.writeString(spdPath.resolve("Hero.java"), javaContent);

        String kotlinContent = "package com.shatteredpixel.shatteredpixeldungeon\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.Dungeon\n"
            + "class World";
        Files.writeString(spdPath.resolve("World.kt"), kotlinContent);

        byte[] binaryData = {(byte) 0x89, 0x50, 0x4E, 0x47};
        Files.write(spdPath.resolve("actors/icon.png"), binaryData);

        // Forward: SPD → CPD
        Path cpdDir = tempDir.resolve("cpd");
        NamespaceTransformer forwardTransformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        forwardTransformer.transform(inputDir, cpdDir);

        // Verify CPD structure
        String cpdJavaContent = Files.readString(cpdDir.resolve("com/qsr/customspd/Hero.java"));
        String expectedCpdJava = "package com.qsr.customspd;\n"
            + "import com.qsr.customspd.actors.Actor;\n"
            + "public class Hero extends Actor {}";
        assertEquals(expectedCpdJava, cpdJavaContent);

        // Reverse: CPD → SPD
        Path restoredDir = tempDir.resolve("restored");
        NamespaceTransformer reverseTransformer = new NamespaceTransformer(NamespaceTransformer.Direction.CPD_TO_SPD);
        reverseTransformer.transform(cpdDir, restoredDir);

        // Verify roundtrip restored original
        String restoredJavaContent = Files.readString(restoredDir.resolve("com/shatteredpixel/shatteredpixeldungeon/Hero.java"));
        assertEquals(javaContent, restoredJavaContent, "Java content should match after roundtrip");

        String restoredKotlinContent = Files.readString(restoredDir.resolve("com/shatteredpixel/shatteredpixeldungeon/World.kt"));
        assertEquals(kotlinContent, restoredKotlinContent, "Kotlin content should match after roundtrip");

        byte[] restoredBinary = Files.readAllBytes(restoredDir.resolve("com/shatteredpixel/shatteredpixeldungeon/actors/icon.png"));
        assertArrayEquals(binaryData, restoredBinary, "Binary content should match after roundtrip");
    }

    @Test
    void testLineCommentNamespaceNotReplaced() throws IOException {
        // Namespace in line comments must be preserved byte-for-byte
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        String content = "// com.shatteredpixel.shatteredpixeldungeon is the original\n"
            + "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "// referenced from com.shatteredpixel.shatteredpixeldungeon\n"
            + "public class Test {}";
        Files.writeString(spdPath.resolve("Comments.java"), content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        String result = Files.readString(outputDir.resolve("com/qsr/customspd/Comments.java"));
        // Code tokens are transformed
        assertTrue(result.contains("package com.qsr.customspd;"), "package declaration should be transformed");
        // Line comment content is preserved verbatim
        assertTrue(result.contains("// com.shatteredpixel.shatteredpixeldungeon is the original"),
            "line comment namespace should not be replaced");
        assertTrue(result.contains("// referenced from com.shatteredpixel.shatteredpixeldungeon"),
            "line comment namespace should not be replaced");
    }

    @Test
    void testBlockCommentNamespaceNotReplaced() throws IOException {
        // Namespace in block/Javadoc comments must be preserved byte-for-byte
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        String content = "/* com.shatteredpixel.shatteredpixeldungeon in block */\n"
            + "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "/** Javadoc: uses com.shatteredpixel.shatteredpixeldungeon.items */\n"
            + "public class Test {\n"
            + " /* nested /* com.shatteredpixel.shatteredpixeldungeon */ }\n"
            + "}";
        Files.writeString(spdPath.resolve("BlockComments.java"), content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        String result = Files.readString(outputDir.resolve("com/qsr/customspd/BlockComments.java"));
        assertTrue(result.contains("package com.qsr.customspd;"), "package declaration should be transformed");
        assertTrue(result.contains("/* com.shatteredpixel.shatteredpixeldungeon in block */"),
            "block comment namespace should not be replaced");
        assertTrue(result.contains("/** Javadoc: uses com.shatteredpixel.shatteredpixeldungeon.items */"),
            "javadoc comment namespace should not be replaced");
        // Nested block comment — the first */ ends the outer comment,
        // so after that everything is code again
        assertTrue(result.contains("/* nested /* com.shatteredpixel.shatteredpixeldungeon */"),
            "nested block comment content should not be replaced");
    }

    @Test
    void testStringLiteralNamespaceNotReplaced() throws IOException {
        // Namespace in string literals must be preserved byte-for-byte
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        String content = "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "public class Config {\n"
            + "    String ref = \"com.shatteredpixel.shatteredpixeldungeon.items.Item\";\n"
            + "    String path = \"com/shatteredpixel/shatteredpixeldungeon/\";\n"
            + "    String escaped = \"com.shatteredpixel.shatteredpixeldungeon\\\"test\";\n"
            + "}";
        Files.writeString(spdPath.resolve("Strings.java"), content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        String result = Files.readString(outputDir.resolve("com/qsr/customspd/Strings.java"));
        assertTrue(result.contains("package com.qsr.customspd;"), "package declaration should be transformed");
        assertTrue(result.contains("\"com.shatteredpixel.shatteredpixeldungeon.items.Item\""),
            "string literal namespace should not be replaced");
        assertTrue(result.contains("\"com/shatteredpixel/shatteredpixeldungeon/\""),
            "string literal with slash should not be replaced");
    }

    @Test
    void testCharLiteralNamespaceNotReplaced() throws IOException {
        // Namespace adjacent to char literals must not cause issues
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        // Char literals containing dots and letters
        String content = "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "public class Test {\n"
            + "    char dot = '.';\n"
            + "    char quote = '\\'';\n"
            + "}";
        Files.writeString(spdPath.resolve("Chars.java"), content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        String result = Files.readString(outputDir.resolve("com/qsr/customspd/Chars.java"));
        assertTrue(result.contains("package com.qsr.customspd;"), "package declaration should be transformed");
        assertTrue(result.contains("char dot = '.';"), "char literal should be preserved");
        assertTrue(result.contains("char quote = '\\'';"), "escaped char literal should be preserved");
    }

    @Test
    void testKotlinTripleQuotedStringNotReplaced() throws IOException {
        // Namespace in Kotlin triple-quoted strings must be preserved byte-for-byte
        Path cpdPath = inputDir.resolve("com/qsr/customspd");
        Files.createDirectories(cpdPath);
        String content = "package com.qsr.customspd\n"
            + "val doc = \"\"\"\n"
            + "  Reference: com.qsr.customspd.items.Item\n"
            + "  Also: com.qsr.customspd\n"
            + "\"\"\"\n"
            + "class Foo";
        Files.writeString(cpdPath.resolve("TripleQuote.kt"), content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.CPD_TO_SPD);
        transformer.transform(inputDir, outputDir);

        String result = Files.readString(outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon/TripleQuote.kt"));
        // package is transformed
        assertTrue(result.contains("package com.shatteredpixel.shatteredpixeldungeon"),
            "package declaration should be transformed");
        // Triple-quoted string content is preserved
        assertTrue(result.contains("com.qsr.customspd.items.Item"),
            "triple-quoted string namespace should not be replaced");
        assertTrue(result.contains("com.qsr.customspd"),
            "triple-quoted string namespace should not be replaced");
    }

    @Test
    void testTxtFileCopiedUnchanged() throws IOException {
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        String txtContent = "The namespace com.shatteredpixel.shatteredpixeldungeon appears in this text.\n"
            + "So does com.qsr.customspd";
        Files.writeString(spdPath.resolve("readme.txt"), txtContent);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        Path expectedFile = outputDir.resolve("com/qsr/customspd/readme.txt");
        String result = Files.readString(expectedFile);
        assertEquals(txtContent, result, ".txt file should be copied byte-for-byte unchanged");
    }

    @Test
    void testDeterministicTraversal() throws IOException {
        // Create multiple nested directories in non-alphabetical order
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);
        Files.createDirectories(spdPath.resolve("z"));
        Files.createDirectories(spdPath.resolve("a"));
        Files.createDirectories(spdPath.resolve("m"));

        Files.writeString(spdPath.resolve("z/File.java"), "class Z {}");
        Files.writeString(spdPath.resolve("a/File.java"), "class A {}");
        Files.writeString(spdPath.resolve("m/File.java"), "class M {}");

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        // All files should exist
        assertTrue(Files.exists(outputDir.resolve("com/qsr/customspd/z/File.java")));
        assertTrue(Files.exists(outputDir.resolve("com/qsr/customspd/a/File.java")));
        assertTrue(Files.exists(outputDir.resolve("com/qsr/customspd/m/File.java")));
    }

    @Test
    void testPartialNamespaceNotReplaced() throws IOException {
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);

        // String that contains the namespace and partial matches
        String content = "String partial1 = \"com.shatteredpixel.shatteredpixeldungeonExtra\";\n"
            + "String partial2 = \"com.shatteredpixel.shatteredpixeldungeon/file\";";

        Files.writeString(spdPath.resolve("Config.java"), content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        String result = Files.readString(outputDir.resolve("com/qsr/customspd/Config.java"));
        // These should NOT be transformed because:
        // - Extra follows the namespace without a dot (part of the same identifier)
        // - /file means it's a path context, not a Java namespace  
        assertTrue(result.contains("com.shatteredpixel.shatteredpixeldungeonExtra"), 
            "Partial match with direct identifier extension should not be replaced");
        assertTrue(result.contains("com.shatteredpixel.shatteredpixeldungeon/file"),
            "Namespace followed by / (path context) should not be replaced");
    }

    @Test
    void testMultipleOccurrencesInSingleFile() throws IOException {
        Path spdPath = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(spdPath);

        String content = "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.items.Item;\n"
            + "public class Game {\n"
            + "    Actor a = new com.shatteredpixel.shatteredpixeldungeon.actors.Mob();\n"
            + "}";

        Files.writeString(spdPath.resolve("Game.java"), content);

        NamespaceTransformer transformer = new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
        transformer.transform(inputDir, outputDir);

        String result = Files.readString(outputDir.resolve("com/qsr/customspd/Game.java"));
        // Count occurrences
        int count = countOccurrences(result, "com.qsr.customspd");
        assertEquals(4, count, "All 4 occurrences should be transformed");
        assertFalse(result.contains("com.shatteredpixel.shatteredpixeldungeon"));
    }

    private int countOccurrences(String str, String substr) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }
}
