package com.qsr.customspd.tools.deletionaudit;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parses Java source into a {@link CallableInventory}. */
public final class InventoryExtractor {

    static {
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    private InventoryExtractor() {
    }

    /**
     * @param path   repo-root-relative path, used only for diagnostics
     * @param source the file's content at some ref
     * @return the inventory; empty if the source cannot be parsed, so one bad
     *         file cannot abort a whole-repository audit
     */
    public static CallableInventory extract(String path, String source) {
        CompilationUnit unit;
        try {
            unit = StaticJavaParser.parse(source);
        } catch (RuntimeException e) {
            System.err.println("deletion-audit: could not parse " + path + ": " + e.getMessage());
            return new CallableInventory(List.of());
        }

        List<CallableInventory.Entry> entries = new ArrayList<>();
        for (CallableDeclaration<?> callable : unit.findAll(CallableDeclaration.class)) {
            entries.add(new CallableInventory.Entry(
                    enclosingTypeName(callable) + "#" + callable.getSignature().asString(),
                    visibilityOf(callable),
                    statementCountOf(callable)));
        }
        return new CallableInventory(entries);
    }

    /**
     * Builds a dotted type name from the callable's ancestor types, so a nested
     * {@code Outer.Inner#run()} never collides with {@code Outer#run()}.
     */
    private static String enclosingTypeName(CallableDeclaration<?> callable) {
        List<String> names = new ArrayList<>();
        Optional<TypeDeclaration> ancestor = callable.findAncestor(TypeDeclaration.class);
        while (ancestor.isPresent()) {
            TypeDeclaration<?> type = ancestor.get();
            names.add(0, type.getNameAsString());
            ancestor = type.findAncestor(TypeDeclaration.class);
        }
        return names.isEmpty() ? "<anonymous>" : String.join(".", names);
    }

    private static String visibilityOf(CallableDeclaration<?> callable) {
        if (callable.isPublic()) return "public";
        if (callable.isProtected()) return "protected";
        if (callable.isPrivate()) return "private";
        return "package";
    }

    /**
     * Counts every statement in the body, nested statements included. An
     * abstract or interface method has no body and counts 0.
     */
    private static int statementCountOf(CallableDeclaration<?> callable) {
        if (callable instanceof MethodDeclaration method) {
            return method.getBody().map(body -> body.findAll(Statement.class).size()).orElse(0);
        }
        if (callable instanceof ConstructorDeclaration constructor) {
            return constructor.getBody().findAll(Statement.class).size();
        }
        return 0;
    }
}
