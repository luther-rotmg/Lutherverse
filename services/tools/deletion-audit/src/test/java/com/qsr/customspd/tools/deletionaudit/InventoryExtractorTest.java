package com.qsr.customspd.tools.deletionaudit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryExtractorTest {

    @Test
    void capturesPrivateMethodsThatApiDiffIgnores() {
        String source = """
                package p;
                class Sample {
                    private void hidden() { int a = 1; }
                }
                """;
        Map<String, CallableInventory.Entry> byKey =
                InventoryExtractor.extract("Sample.java", source).byKey();

        assertTrue(byKey.containsKey("Sample#hidden()"),
                "private methods must be inventoried; api-diff cannot see them");
        assertEquals("private", byKey.get("Sample#hidden()").visibility());
    }

    @Test
    void countsStatementsIncludingNestedOnes() {
        String source = """
                package p;
                class Sample {
                    void body() {
                        int a = 1;
                        if (a > 0) {
                            a++;
                        }
                    }
                }
                """;
        CallableInventory.Entry entry =
                InventoryExtractor.extract("Sample.java", source).byKey().get("Sample#body()");

        // findAll() includes the root node, so the method's own body block counts:
        //   1 body BlockStmt, 2 "int a = 1", 3 the IfStmt, 4 the if's BlockStmt, 5 "a++"
        // Every method with a body therefore has a floor of 1. That is fine: the
        // invariant this tool needs is that the count is deterministic and drops
        // when statements are removed, not that it equals any particular total.
        assertEquals(5, entry.statementCount());
    }

    @Test
    void keysIncludeParameterTypesSoOverloadsStaySeparate() {
        String source = """
                package p;
                class Sample {
                    void go(int a) {}
                    void go(String a) {}
                }
                """;
        Map<String, CallableInventory.Entry> byKey =
                InventoryExtractor.extract("Sample.java", source).byKey();

        assertTrue(byKey.containsKey("Sample#go(int)"));
        assertTrue(byKey.containsKey("Sample#go(String)"));
    }

    @Test
    void nestedTypesAreQualifiedByTheirOuterType() {
        String source = """
                package p;
                class Outer {
                    static class Inner {
                        void run() {}
                    }
                }
                """;
        assertTrue(InventoryExtractor.extract("Outer.java", source).byKey()
                        .containsKey("Outer.Inner#run()"),
                "nested type callables must not collide with outer-type ones");
    }

    @Test
    void constructorsAreInventoried() {
        String source = """
                package p;
                class Sample {
                    Sample(int a) {}
                }
                """;
        assertTrue(InventoryExtractor.extract("Sample.java", source).byKey()
                .containsKey("Sample#Sample(int)"));
    }

    @Test
    void abstractMethodHasZeroStatementsRatherThanFailing() {
        String source = """
                package p;
                abstract class Sample {
                    abstract void todo();
                }
                """;
        assertEquals(0, InventoryExtractor.extract("Sample.java", source)
                .byKey().get("Sample#todo()").statementCount());
    }

    @Test
    void unparseableSourceYieldsEmptyInventoryRatherThanThrowing() {
        CallableInventory inventory = InventoryExtractor.extract("Broken.java", "this is not java {{{");
        assertTrue(inventory.entries().isEmpty(),
                "a parse failure must degrade to empty, not abort a whole-repo audit");
    }
}
