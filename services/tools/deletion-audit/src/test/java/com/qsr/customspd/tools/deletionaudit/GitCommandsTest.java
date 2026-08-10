package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitCommandsTest {

    @Test
    void repoRootResolvesFromSubprojectWorkingDirectory() throws IOException {
        // The JVM running this test has its CWD set to the subproject by Gradle.
        // repoRoot() must still resolve the repository root, or every other
        // command in this tool silently scans the wrong tree.
        assertNotNull(GitCommands.repoRoot(), "repository root must resolve");
    }

    @Test
    void listTreeReturnsPathsOutsideThisSubproject() throws IOException {
        List<String> paths = GitCommands.listTree("HEAD");
        assertFalse(paths.isEmpty(), "HEAD tree must not be empty");
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("core/")),
                "listTree must return repo-root-relative paths, including core/");
    }

    @Test
    void readBlobReturnsFileContentAtRef() throws IOException {
        String content = GitCommands.readBlob("HEAD", "settings.gradle");
        // Asserts on a registration that predates this module, so the test does
        // not depend on its own commit having landed yet.
        assertTrue(content.contains("include ':core'"),
                "settings.gradle at HEAD must register the core module");
    }

    @Test
    void missingPathIsRecognisedRatherThanCrashing() {
        IOException thrown = assertThrows(IOException.class,
                () -> GitCommands.readBlob("HEAD", "no/such/file/anywhere.java"));
        assertTrue(GitCommands.isPathNotFound(thrown),
                "an absent path must be classified as not-found, not as a git failure");
    }
}
