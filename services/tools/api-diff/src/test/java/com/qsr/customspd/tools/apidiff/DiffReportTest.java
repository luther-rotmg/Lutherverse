package com.qsr.customspd.tools.apidiff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffReportTest {

    @Test
    void reportsRemovedAddedAndSignatureChangedSymbols() {
        JavaSurface before = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "baz()", "public", "String"),
                new JavaSurface.Symbol("com.example.Foo", "qux()", "public", "void")
        ));
        JavaSurface after = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "baz(String)", "public", "String"),
                new JavaSurface.Symbol("com.example.Foo", "quux()", "public", "void")
        ));

        DiffReport report = DiffReport.compare(before, after);

        assertEquals(1, report.removed().size());
        assertEquals(1, report.added().size());
        assertEquals(1, report.signatureChanged().size());

        assertTrue(report.removed().contains(
                new DiffReport.Removed(new JavaSurface.Symbol("com.example.Foo", "qux()", "public", "void"))));
        assertTrue(report.added().contains(
                new DiffReport.Added(new JavaSurface.Symbol("com.example.Foo", "quux()", "public", "void"))));
        assertTrue(report.signatureChanged().contains(new DiffReport.SignatureChanged(
                new JavaSurface.Symbol("com.example.Foo", "baz()", "public", "String"),
                new JavaSurface.Symbol("com.example.Foo", "baz(String)", "public", "String"))));
    }

    @Test
    void reportsRemovalOfOneOverloadedMethod() {
        JavaSurface before = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "bar(String)", "public", "void")
        ));
        JavaSurface after = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void")
        ));

        DiffReport report = DiffReport.compare(before, after);

        assertEquals(1, report.removed().size());
        assertEquals(0, report.added().size());
        assertEquals(0, report.signatureChanged().size());

        assertTrue(report.removed().contains(
                new DiffReport.Removed(new JavaSurface.Symbol("com.example.Foo", "bar(String)", "public", "void"))));
    }

    @Test
    void reportsSignatureChangeToOneOverloadedMethod() {
        JavaSurface before = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "bar(String)", "public", "void")
        ));
        JavaSurface after = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(long)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "bar(String)", "public", "void")
        ));

        DiffReport report = DiffReport.compare(before, after);

        // bar(int) changed to bar(long); no removed/added since exactly one
        // unmatched on each side, reported as SignatureChanged instead.
        assertEquals(0, report.removed().size());
        assertEquals(0, report.added().size());
        assertEquals(1, report.signatureChanged().size());

        assertTrue(report.signatureChanged().contains(new DiffReport.SignatureChanged(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "bar(long)", "public", "void"))));
    }

    @Test
    void reportsOneSignatureChangedWhenOneOverloadUnchanged() {
        // Two overloads, one unchanged and one signature-changed.
        JavaSurface before = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "bar(String)", "public", "void")
        ));
        JavaSurface after = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(long)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "bar(String)", "public", "void")
        ));

        DiffReport report = DiffReport.compare(before, after);

        // bar(String) is identical on both sides - should be excluded.
        // bar(int) → bar(long) is the only unmatched pair → SignatureChanged.
        assertEquals(0, report.removed().size());
        assertEquals(0, report.added().size());
        assertEquals(1, report.signatureChanged().size());

        assertTrue(report.signatureChanged().contains(new DiffReport.SignatureChanged(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "bar(long)", "public", "void"))));
    }

    @Test
    void reportsReturnTypeChangeToOneOverload() {
        JavaSurface before = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "int"),
                new JavaSurface.Symbol("com.example.Foo", "bar(String)", "public", "void")));
        JavaSurface after = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "bar(int)", "public", "long"),
                new JavaSurface.Symbol("com.example.Foo", "bar(String)", "public", "void")));

        DiffReport report = DiffReport.compare(before, after);

        assertEquals(1, report.signatureChanged().size());
        assertEquals("int", report.signatureChanged().get(0).before().returnType());
        assertEquals("long", report.signatureChanged().get(0).after().returnType());
    }

    @Test
    void preservesSourceOrderAcrossMemberGroups() {
        JavaSurface before = new JavaSurface(List.of(
                new JavaSurface.Symbol("com.example.Foo", "z()", "public", "void"),
                new JavaSurface.Symbol("com.example.Foo", "a()", "public", "void")));

        DiffReport report = DiffReport.compare(before, new JavaSurface(List.of()));

        assertEquals(List.of("z()", "a()"), report.removed().stream()
                .map(removed -> removed.symbol().signature()).toList());
    }
}
