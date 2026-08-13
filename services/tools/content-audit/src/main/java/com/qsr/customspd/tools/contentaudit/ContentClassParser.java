package com.qsr.customspd.tools.contentaudit;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;

/** Parses one Java source file into a {@link ContentClass}. */
public final class ContentClassParser {
    private ContentClassParser() {}

    public static ContentClass parse(String source) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(source);
        } catch (RuntimeException e) {
            return null; // unparseable source is not content; callers skip nulls
        }
        ClassOrInterfaceDeclaration type = cu.findFirst(ClassOrInterfaceDeclaration.class)
                .filter(t -> !t.isInterface()).orElse(null);
        if (type == null) return null;

        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        String sup = type.getExtendedTypes().isEmpty()
                ? null : type.getExtendedTypes(0).getNameAsString();

        String spriteClass = assignedSimpleName(type, "spriteClass", /*classExpr=*/true);
        String imageAsset = assignedSimpleName(type, "image", /*classExpr=*/false);

        return new ContentClass(type.getNameAsString(), pkg, sup, type.isAbstract(),
                spriteClass, imageAsset);
    }

    /** Finds {@code <field> = X.class} (classExpr) or {@code <field> = Enum.MEMBER}
     *  (field access) anywhere in the class body and returns X / MEMBER, or null. */
    private static String assignedSimpleName(ClassOrInterfaceDeclaration type,
                                             String field, boolean classExpr) {
        for (AssignExpr assign : type.findAll(AssignExpr.class)) {
            if (!(assign.getTarget() instanceof NameExpr target)) continue;
            if (!target.getNameAsString().equals(field)) continue;
            Expression value = assign.getValue();
            if (classExpr && value instanceof ClassExpr ce) {
                return ce.getType().asString();
            }
            if (!classExpr && value instanceof FieldAccessExpr fa) {
                return fa.getNameAsString(); // e.g. GeneralAsset.SUPPLY_RATION -> SUPPLY_RATION
            }
        }
        return null;
    }
}
