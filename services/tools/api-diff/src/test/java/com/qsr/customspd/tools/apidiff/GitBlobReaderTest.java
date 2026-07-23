package com.qsr.customspd.tools.apidiff;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitBlobReaderTest {

    @Test
    void readsFileContentAtGivenRef() throws IOException {
        String content = GitBlobReader.read("HEAD", "README.md");

        assertTrue(content.startsWith("<p align=\"center\">"));
        assertTrue(content.contains("Lutherverse"));
    }

    @Test
    void throwsIOExceptionWhenPathDoesNotExistAtRef() {
        IOException ex = assertThrows(IOException.class,
                () -> GitBlobReader.read("HEAD", "definitely/does/not/exist/Nope.java"));
        assertTrue(ex.getMessage().contains("does not exist in"),
                "Message should indicate path-not-found: " + ex.getMessage());
    }

    @Test
    void throwsIOExceptionForInvalidGitRef() {
        // A non-existent ref is a genuine failure, not a missing file.
        IOException ex = assertThrows(IOException.class,
                () -> GitBlobReader.read("THIS_REF_DOES_NOT_EXIST_XYZ", "README.md"));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("failed with exit code"),
                "Message should indicate process failure: " + ex.getMessage());
    }
}
