package com.qsr.customspd.tools.nstransform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private Path spdDir() throws IOException {
        Path p = inputDir.resolve("com/shatteredpixel/shatteredpixeldungeon");
        Files.createDirectories(p);
        return p;
    }
    private Path cpdDir() throws IOException {
        Path p = inputDir.resolve("com/qsr/customspd");
        Files.createDirectories(p);
        return p;
    }
    private void write(Path dir, String file, String content) throws IOException {
        Files.writeString(dir.resolve(file), content);
    }
    private static NamespaceTransformer spdToCpd() {
        return new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD);
    }
    private static NamespaceTransformer cpdToSpd() {
        return new NamespaceTransformer(NamespaceTransformer.Direction.CPD_TO_SPD);
    }

@Test
    void testSpdToCpdPathTransformation() throws IOException {
        write(spdDir(), "Foo.java", "class Foo {}");
        spdToCpd().transform(inputDir, outputDir);
        assertTrue(Files.exists(outputDir.resolve("com/qsr/customspd/Foo.java")));
        assertEquals("class Foo {}", Files.readString(outputDir.resolve("com/qsr/customspd/Foo.java")));
        assertFalse(Files.exists(outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon")));
    }
    @Test
    void testCpdToSpdPathTransformation() throws IOException {
        write(cpdDir(), "Bar.kt", "class Bar {}");
        cpdToSpd().transform(inputDir, outputDir);
        assertTrue(Files.exists(outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon/Bar.kt")));
        assertEquals("class Bar {}", Files.readString(outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon/Bar.kt")));
    }
    @Test
    void testSpdToCpdJavaImportTransformation() throws IOException {
        String content = "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.items.Item;\n"
            + "public class Hero extends Char {\n"
            + "    // Uses Item\n"
            + "}";
        write(spdDir(), "Hero.java", content);
        spdToCpd().transform(inputDir, outputDir);
        String expected = "package com.qsr.customspd;\n"
            + "import com.qsr.customspd.items.Item;\n"
            + "public class Hero extends Char {\n"
            + "    // Uses Item\n"
            + "}";
        assertEquals(expected, Files.readString(outputDir.resolve("com/qsr/customspd/Hero.java")));
    }
    @Test
    void testCpdToSpdKotlinImportTransformation() throws IOException {
        String content = "package com.qsr.customspd\n"
            + "import com.qsr.customspd.levels.Level\n"
            + "class Dungeon {\n"
            + "    val level: Level? = null\n"
            + "}";
        write(cpdDir(), "Dungeon.kt", content);
        cpdToSpd().transform(inputDir, outputDir);
        String expected = "package com.shatteredpixel.shatteredpixeldungeon\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.levels.Level\n"
            + "class Dungeon {\n"
            + "    val level: Level? = null\n"
            + "}";
        assertEquals(expected, Files.readString(outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon/Dungeon.kt")));
    }
    @Test
    void testNestedSourcePaths() throws IOException {
        Path spd = spdDir();
        Files.createDirectories(spd.resolve("levels"));
        Files.createDirectories(spd.resolve("items/armor"));
        write(spd, "Char.java", "class Char {}");
        write(spd, "levels/Level.java", "class Level {}");
        write(spd, "items/Item.java", "class Item {}");
        write(spd, "items/armor/Plate.java", "class Plate {}");
        spdToCpd().transform(inputDir, outputDir);
        Path cpd = outputDir.resolve("com/qsr/customspd");
        assertTrue(Files.exists(cpd.resolve("Char.java")));
        assertTrue(Files.exists(cpd.resolve("levels/Level.java")));
        assertTrue(Files.exists(cpd.resolve("items/Item.java")));
        assertTrue(Files.exists(cpd.resolve("items/armor/Plate.java")));
    }
    @Test
    void testBinaryBytePreservation() throws IOException {
        byte[] binaryContent = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        Files.write(spdDir().resolve("image.jpg"), binaryContent);
        spdToCpd().transform(inputDir, outputDir);
        assertArrayEquals(binaryContent, Files.readAllBytes(outputDir.resolve("com/qsr/customspd/image.jpg")));
    }
    @Test
    void testRefusalOfInPlaceTransform() throws IOException {
        write(spdDir(), "Test.java", "class Test {}");
        assertThrows(IllegalArgumentException.class, () -> spdToCpd().transform(inputDir, inputDir));
    }
    @Test
    void testDestinationCollisionRefusal() throws IOException {
        write(spdDir(), "FileA.java", "class FileA {}");
        Files.createDirectories(inputDir.resolve("com/qsr/customspd"));
        write(inputDir.resolve("com/qsr/customspd"), "FileA.java", "class FileA {}");
        assertThrows(IllegalArgumentException.class, () -> spdToCpd().transform(inputDir, outputDir));
    }
    @Test
    void testForwardAndReverseRoundtrip() throws IOException {
        Path spd = spdDir();
        Files.createDirectories(spd.resolve("actors"));
        String javaContent = "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;\n"
            + "public class Hero extends Actor {}";
        write(spd, "Hero.java", javaContent);
        String kotlinContent = "package com.shatteredpixel.shatteredpixeldungeon\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.Dungeon\n"
            + "class World";
        write(spd, "World.kt", kotlinContent);
        byte[] binaryData = {(byte) 0x89, 0x50, 0x4E, 0x47};
        Files.write(spd.resolve("actors/icon.png"), binaryData);
        Path cpdDir = tempDir.resolve("cpd");
        new NamespaceTransformer(NamespaceTransformer.Direction.SPD_TO_CPD).transform(inputDir, cpdDir);
        assertEquals("package com.qsr.customspd;\n"
            + "import com.qsr.customspd.actors.Actor;\n"
            + "public class Hero extends Actor {}",
            Files.readString(cpdDir.resolve("com/qsr/customspd/Hero.java")));
        Path restoredDir = tempDir.resolve("restored");
        new NamespaceTransformer(NamespaceTransformer.Direction.CPD_TO_SPD).transform(cpdDir, restoredDir);
        assertEquals(javaContent, Files.readString(restoredDir.resolve("com/shatteredpixel/shatteredpixeldungeon/Hero.java")));
        assertEquals(kotlinContent, Files.readString(restoredDir.resolve("com/shatteredpixel/shatteredpixeldungeon/World.kt")));
        assertArrayEquals(binaryData, Files.readAllBytes(restoredDir.resolve("com/shatteredpixel/shatteredpixeldungeon/actors/icon.png")));
    }
    @Test
    void testCommentsAndStringsNamespacePreserved() throws IOException {
        // Line, block, nested block, string, char literals
        String content = "// com.shatteredpixel.shatteredpixeldungeon is the original\n"
            + "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "/* com.shatteredpixel.shatteredpixeldungeon in block */\n"
            + "/** Javadoc: uses com.shatteredpixel.shatteredpixeldungeon.items */\n"
            + "public class Test {\n"
            + "    /* nested /* com.shatteredpixel.shatteredpixeldungeon */ }\n"
            + "    String ref = \"com.shatteredpixel.shatteredpixeldungeon.items.Item\";\n"
            + "    String path = \"com/shatteredpixel.shatteredpixeldungeon/\";\n"
            + "    char dot = '.';\n"
            + "    char quote = '\\'';\n"
            + "}";
        write(spdDir(), "Comments.java", content);
        spdToCpd().transform(inputDir, outputDir);
        String result = Files.readString(outputDir.resolve("com/qsr/customspd/Comments.java"));
        assertTrue(result.contains("package com.qsr.customspd;"));
        assertTrue(result.contains("// com.shatteredpixel.shatteredpixeldungeon is the original"));
        assertTrue(result.contains("/* com.shatteredpixel.shatteredpixeldungeon in block */"));
        assertTrue(result.contains("/** Javadoc: uses com.shatteredpixel.shatteredpixeldungeon.items */"));
        assertTrue(result.contains("/* nested /* com.shatteredpixel.shatteredpixeldungeon */"));
        assertTrue(result.contains("\"com.shatteredpixel.shatteredpixeldungeon.items.Item\""));
        assertTrue(result.contains("\"com/shatteredpixel.shatteredpixeldungeon/\""));
        assertTrue(result.contains("char dot = '.';"));
        assertTrue(result.contains("char quote = '\\'';"));
    }
    @Test
    void testKotlinTripleQuotedStringNotReplaced() throws IOException {
        String content = "package com.qsr.customspd\n"
            + "val doc = \"\"\"\n"
            + "  Reference: com.qsr.customspd.items.Item\n"
            + "  Also: com.qsr.customspd\n"
            + "\"\"\"\n"
            + "class Foo";
        write(cpdDir(), "TripleQuote.kt", content);
        cpdToSpd().transform(inputDir, outputDir);
        String result = Files.readString(outputDir.resolve("com/shatteredpixel/shatteredpixeldungeon/TripleQuote.kt"));
        assertTrue(result.contains("package com.shatteredpixel.shatteredpixeldungeon"));
        assertTrue(result.contains("com.qsr.customspd.items.Item"));
        assertTrue(result.contains("com.qsr.customspd"));
    }
    @Test
    void testTxtFileCopiedUnchanged() throws IOException {
        String txtContent = "The namespace com.shatteredpixel.shatteredpixeldungeon appears in this text.\n"
            + "So does com.qsr.customspd";
        write(spdDir(), "readme.txt", txtContent);
        spdToCpd().transform(inputDir, outputDir);
        assertEquals(txtContent, Files.readString(outputDir.resolve("com/qsr/customspd/readme.txt")));
    }
    @Test
    void testDeterministicTraversal() throws IOException {
        Path spd = spdDir();
        Files.createDirectories(spd.resolve("z"));
        Files.createDirectories(spd.resolve("a"));
        Files.createDirectories(spd.resolve("m"));
        write(spd, "z/File.java", "class Z {}");
        write(spd, "a/File.java", "class A {}");
        write(spd, "m/File.java", "class M {}");
        spdToCpd().transform(inputDir, outputDir);
        Path cpd = outputDir.resolve("com/qsr/customspd");
        assertTrue(Files.exists(cpd.resolve("z/File.java")));
        assertTrue(Files.exists(cpd.resolve("a/File.java")));
        assertTrue(Files.exists(cpd.resolve("m/File.java")));
    }
    @Test
    void testPartialNamespaceNotReplaced() throws IOException {
        String content = "String partial1 = \"com.shatteredpixel.shatteredpixeldungeonExtra\";\n"
            + "String partial2 = \"com.shatteredpixel.shatteredpixeldungeon/file\";";
        write(spdDir(), "Config.java", content);
        spdToCpd().transform(inputDir, outputDir);
        String result = Files.readString(outputDir.resolve("com/qsr/customspd/Config.java"));
        assertTrue(result.contains("com.shatteredpixel.shatteredpixeldungeonExtra"));
        assertTrue(result.contains("com.shatteredpixel.shatteredpixeldungeon/file"));
    }
    @Test
    void testMultipleOccurrencesInSingleFile() throws IOException {
        String content = "package com.shatteredpixel.shatteredpixeldungeon;\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;\n"
            + "import com.shatteredpixel.shatteredpixeldungeon.items.Item;\n"
            + "public class Game {\n"
            + "    Actor a = new com.shatteredpixel.shatteredpixeldungeon.actors.Mob();\n"
            + "}";
        write(spdDir(), "Game.java", content);
        spdToCpd().transform(inputDir, outputDir);
        String result = Files.readString(outputDir.resolve("com/qsr/customspd/Game.java"));
        assertEquals(4, countOccurrences(result, "com.qsr.customspd"));
        assertFalse(result.contains("com.shatteredpixel.shatteredpixeldungeon"));
    }
    private static int countOccurrences(String str, String substr) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(substr, idx)) != -1) {
            count++;
            idx += substr.length();
        }
        return count;
    }
}
