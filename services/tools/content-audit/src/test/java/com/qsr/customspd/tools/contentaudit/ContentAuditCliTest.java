package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentAuditCliTest {
    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    /** Fixture repo: one wired mob (Rat) and one broken mob (Ghost, no localization/sprite/registration). */
    private static File fixture(Path root) throws Exception {
        write(root.resolve("core/src/main/java/com/qsr/customspd/assets/GeneralAsset.kt"),
                "enum class GeneralAsset(val path: String) {\n"
                        + "    RAT(\"sprites/mobs/rat.png\"),\n}");
        write(root.resolve("core/src/main/assets/sprites/mobs/rat.png"), "png");
        write(root.resolve("core/src/main/assets/messages/actors/actors.properties"),
                "actors.mobs.rat.name=rat\nactors.mobs.rat.desc=nasty\n");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Bestiary.kt"),
                "if (cl == Rat::class.java) {}");
        write(root.resolve("core/src/main/java/com/qsr/customspd/items/Generator.java"), "class Generator {}");
        write(root.resolve("core/src/main/java/com/qsr/customspd/sprites/RatSprite.java"),
                "class RatSprite { { texture(GeneralAsset.RAT); } }");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Mob.java"),
                "public abstract class Mob {}");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Rat.java"),
                "public class Rat extends Mob { { spriteClass = RatSprite.class; } }");
        write(root.resolve("core/src/main/java/com/qsr/customspd/actors/mobs/Ghost.java"),
                "public class Ghost extends Mob { { spriteClass = GhostSprite.class; } }");
        return root.toFile();
    }

    @Test
    void findsTheBrokenEntityButNotTheWiredOne(@TempDir Path root) throws Exception {
        ContentAuditCli.Result r = ContentAuditCli.run(fixture(root), Allowlist.load(null));
        assertEquals(2, r.entitiesScanned());
        assertTrue(r.findings().stream().anyMatch(f -> f.key().startsWith("Mob Ghost#")));
        assertFalse(r.findings().stream().anyMatch(f -> f.key().startsWith("Mob Rat#")));
    }

    @Test
    void allowlistSuppressesAKnownException(@TempDir Path root) throws Exception {
        File repo = fixture(root);
        Path al = root.resolve("exceptions.txt");
        Files.writeString(al, "Mob Ghost#M1\nMob Ghost#M2\nMob Ghost#M3\n");
        ContentAuditCli.Result r = ContentAuditCli.run(repo, Allowlist.load(al));
        assertTrue(r.findings().isEmpty());
    }

    @Test
    void canaryCatchesTheDeliberatelyBrokenEntity() {
        // Standing negative control: if this ever returns non-zero, the checks have
        // stopped flagging a deliberately-broken entity and the whole audit is inert.
        assertEquals(0, ContentAuditCli.runCanary());
    }

    @Test
    void relativeAllowlistResolvesAgainstRepoRootAndAbsolutePassesThrough(@TempDir Path root) {
        java.io.File repo = root.toFile();
        // relative path resolves against the repo root, NOT the subproject CWD
        java.nio.file.Path rel = java.nio.file.Path.of("services/tools/content-audit/reviewed-exceptions.txt");
        org.junit.jupiter.api.Assertions.assertEquals(
                root.resolve("services/tools/content-audit/reviewed-exceptions.txt"),
                ContentAuditCli.resolve(repo, rel));
        // an absolute path passes through unchanged
        java.nio.file.Path abs = root.resolve("abs.txt").toAbsolutePath();
        org.junit.jupiter.api.Assertions.assertEquals(abs, ContentAuditCli.resolve(repo, abs));
        // null passes through
        org.junit.jupiter.api.Assertions.assertNull(ContentAuditCli.resolve(repo, null));
    }
}
