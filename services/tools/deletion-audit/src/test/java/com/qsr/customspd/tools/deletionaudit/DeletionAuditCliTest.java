package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeletionAuditCliTest {

    @Test
    void identicalRefsProduceNoFindings() throws IOException {
        DeletionAuditCli.Result result = DeletionAuditCli.run(
                "HEAD", "HEAD", "core/src/main/java/**/*.java", 3, Allowlist.load(null));

        assertTrue(result.filesScanned() > 0,
                "scanning HEAD against itself must still walk the tree; a zero here is "
                        + "the api-diff failure mode repeating");
        assertFalse(result.hasFindings(), "HEAD vs HEAD cannot have removals");
    }

    @Test
    void globIsAppliedAgainstRepoRootRelativePaths() throws IOException {
        DeletionAuditCli.Result result = DeletionAuditCli.run(
                "HEAD", "HEAD", "SPD-classes/src/main/java/**/*.java", 3, Allowlist.load(null));

        assertTrue(result.filesScanned() > 0, "SPD-classes sources must match the glob");
    }

    @Test
    void theCheckedInAllowlistLoadsWhenResolvedFromTheRepoRoot() throws IOException {
        // Regression: `gradle run` sets the CWD to the subproject, so the
        // repo-root-relative allowlist path did not exist and loaded empty --
        // silently re-reporting every reviewed removal. Same defect class as
        // api-diff scanning zero files.
        Path fromRoot = GitCommands.repoRoot().toPath()
                .resolve("services/tools/deletion-audit/reviewed-removals.txt");

        assertTrue(Files.exists(fromRoot), "the checked-in allowlist must resolve from the repo root");
        assertTrue(Allowlist.load(fromRoot).permits("Room#canMerge(Level, Point, int)"),
                "a known reviewed key must be permitted; if this fails the allowlist "
                        + "silently loaded empty");
    }

    @Test
    void aGlobMatchingNothingScansZeroFiles() throws IOException {
        DeletionAuditCli.Result result = DeletionAuditCli.run(
                "HEAD", "HEAD", "no/such/dir/**/*.java", 3, Allowlist.load(null));

        assertEquals(0, result.filesScanned());
    }
}
