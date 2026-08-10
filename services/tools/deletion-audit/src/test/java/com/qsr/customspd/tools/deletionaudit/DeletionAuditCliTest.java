package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.io.IOException;

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
    void aGlobMatchingNothingScansZeroFiles() throws IOException {
        DeletionAuditCli.Result result = DeletionAuditCli.run(
                "HEAD", "HEAD", "no/such/dir/**/*.java", 3, Allowlist.load(null));

        assertEquals(0, result.filesScanned());
    }
}
