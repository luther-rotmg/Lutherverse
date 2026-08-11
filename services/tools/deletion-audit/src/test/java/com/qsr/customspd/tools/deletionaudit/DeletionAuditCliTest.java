package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

    // --- Rename detection ---
    //
    // Exercised against a throwaway git repository, isolated from this project's own
    // history, so the fixture can guarantee a pure rename: identical content, no
    // unrelated changes riding along.

    @Test
    void detectRenamesMapsOldPathToNewPath(@TempDir Path repoDir) throws IOException, InterruptedException {
        String source = "package p; class Foo { void bar() { int x = 1; } }";
        String baseSha = commitFile(repoDir, "old/Foo.java", source, "add Foo");
        renameFile(repoDir, "old/Foo.java", "new/Foo.java");
        String headSha = commitAll(repoDir, "move Foo");

        Map<String, String> renames = GitCommands.detectRenames(repoDir.toFile(), baseSha, headSha);

        assertEquals("new/Foo.java", renames.get("old/Foo.java"));
    }

    @Test
    void renamedFileWithUnchangedContentsYieldsZeroFindings(@TempDir Path repoDir)
            throws IOException, InterruptedException {
        String source = "package p; class Foo { void bar() { int x = 1; int y = 2; } }";
        String baseSha = commitFile(repoDir, "old/Foo.java", source, "add Foo");
        renameFile(repoDir, "old/Foo.java", "new/Foo.java");
        String headSha = commitAll(repoDir, "move Foo");

        DeletionAuditCli.Result result = DeletionAuditCli.run(
                repoDir.toFile(), baseSha, headSha, "**/*.java", 3, Allowlist.load(null));

        assertFalse(result.hasFindings(),
                "a pure rename with unchanged content must not report the old path's "
                        + "callables as deleted");
    }

    @Test
    void withoutConsultingTheRenameMapTheSamePathsWouldFalselyReportEveryCallableDeleted(
            @TempDir Path repoDir) throws IOException, InterruptedException {
        // Documents the defect this fixes: comparing the old path's base inventory
        // against an empty inventory (what the old path resolves to at head, since it
        // no longer exists there) reports every callable as deleted, even though the
        // file only moved.
        String source = "package p; class Foo { void bar() { int x = 1; int y = 2; } }";
        String baseSha = commitFile(repoDir, "old/Foo.java", source, "add Foo");
        renameFile(repoDir, "old/Foo.java", "new/Foo.java");
        commitAll(repoDir, "move Foo");

        CallableInventory before = InventoryExtractor.extract("old/Foo.java",
                GitCommands.readBlob(repoDir.toFile(), baseSha, "old/Foo.java"));
        InventoryDiff.Report naive = InventoryDiff.compare(before, new CallableInventory(List.of()), 3);

        assertFalse(naive.deleted().isEmpty(),
                "sanity check: comparing against an empty head inventory must reproduce "
                        + "the false-positive rename detection guards against");
    }

    private static String commitFile(Path repoDir, String relativePath, String content, String message)
            throws IOException, InterruptedException {
        runGit(repoDir, "init", "-q");
        runGit(repoDir, "config", "user.email", "test@example.com");
        runGit(repoDir, "config", "user.name", "Test");
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        runGit(repoDir, "add", relativePath);
        runGit(repoDir, "commit", "-q", "-m", message);
        return runGit(repoDir, "rev-parse", "HEAD").trim();
    }

    private static void renameFile(Path repoDir, String from, String to) throws IOException, InterruptedException {
        Files.createDirectories(repoDir.resolve(to).getParent());
        runGit(repoDir, "mv", from, to);
    }

    private static String commitAll(Path repoDir, String message) throws IOException, InterruptedException {
        runGit(repoDir, "commit", "-q", "-m", message);
        return runGit(repoDir, "rev-parse", "HEAD").trim();
    }

    private static String runGit(Path workingDir, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException(String.join(" ", command) + " failed (" + exit + "): " + output);
        }
        return output;
    }
}
