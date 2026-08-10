package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowlistTest {

    @Test
    void permitsKeysListedInTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("allow.txt");
        Files.writeString(file, """
                # reviewed 2026-08-10: superseded by CPDU's pack-config music routing
                CorpseDust#actions(Hero)

                Mob#die(Object)
                """);

        Allowlist allowlist = Allowlist.load(file);

        assertTrue(allowlist.permits("CorpseDust#actions(Hero)"));
        assertTrue(allowlist.permits("Mob#die(Object)"));
    }

    @Test
    void rejectsKeysNotListed(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("allow.txt");
        Files.writeString(file, "CorpseDust#actions(Hero)\n");

        assertFalse(Allowlist.load(file).permits("Hero#something()"));
    }

    @Test
    void commentsAndBlankLinesAreNotKeys(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("allow.txt");
        Files.writeString(file, "# a comment\n\n");

        assertFalse(Allowlist.load(file).permits("# a comment"));
        assertFalse(Allowlist.load(file).permits(""));
    }

    @Test
    void aNullPathYieldsAnAllowlistThatPermitsNothing() throws IOException {
        assertFalse(Allowlist.load(null).permits("Anything#at(All)"));
    }

    @Test
    void anAbsentFileYieldsAnAllowlistThatPermitsNothing(@TempDir Path dir) throws IOException {
        assertFalse(Allowlist.load(dir.resolve("missing.txt")).permits("Anything#at(All)"));
    }
}
