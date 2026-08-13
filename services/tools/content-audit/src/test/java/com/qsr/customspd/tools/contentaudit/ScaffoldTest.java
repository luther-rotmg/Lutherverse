package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

class ScaffoldTest {
    @Test
    void javaParserIsOnTheClasspath() {
        // Proves the module builds and JavaParser resolves before any real code exists.
        assertTrue(StaticJavaParser.parse("class A {}").getType(0).getNameAsString().equals("A"));
    }
}
