package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AllowlistTest {
    @Test
    void permitsListedKeysAndIgnoresCommentsAndBlanks(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("exceptions.txt");
        Files.writeString(f, "# a comment\n\nMob YogDzewa#M3\nItem Amulet#I3\n");
        Allowlist a = Allowlist.load(f);
        assertTrue(a.permits("Mob YogDzewa#M3"));
        assertTrue(a.permits("Item Amulet#I3"));
        assertFalse(a.permits("# a comment"));
        assertFalse(a.permits("Mob Rat#M2"));
    }

    @Test
    void absentPathPermitsNothing() throws Exception {
        Allowlist a = Allowlist.load(Path.of("does-not-exist.txt"));
        assertFalse(a.permits("Mob Rat#M2"));
    }
}
