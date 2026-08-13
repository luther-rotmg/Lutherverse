package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ScaffoldModuleTest {
    @Test
    void placeholderResourceIsOnTheClasspath() {
        assertNotNull(getClass().getResource("/placeholder.png"), "placeholder.png must be a bundled resource");
    }

    @Test
    void contentAuditDependencyResolves() {
        // Proves the cross-module dependency is wired: reference a content-audit type.
        assertNotNull(com.qsr.customspd.tools.contentaudit.RepoRoot.class);
    }
}
