package com.qsr.customspd.tools.apidiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The delta between two {@link JavaSurface} snapshots of the same file
 * (typically the same path at two git refs).
 */
public record DiffReport(List<Removed> removed, List<Added> added, List<SignatureChanged> signatureChanged) {

    public record Removed(JavaSurface.Symbol symbol) {
    }

    public record Added(JavaSurface.Symbol symbol) {
    }

    public record SignatureChanged(JavaSurface.Symbol before, JavaSurface.Symbol after) {
    }

    public static DiffReport compare(JavaSurface before, JavaSurface after) {
        Map<String, List<JavaSurface.Symbol>> beforeByName = groupByName(before);
        Map<String, List<JavaSurface.Symbol>> afterByName = groupByName(after);

        List<Removed> removedList = new ArrayList<>();
        List<Added> addedList = new ArrayList<>();
        List<SignatureChanged> changedList = new ArrayList<>();

        // Compare each name group: if no overloads, the existing simple
        // matching works (signature change detected via equals).
        // If there are overloads, disambiguate by full identity.
        Set<String> allNames = new LinkedHashSet<>(beforeByName.keySet());
        allNames.addAll(afterByName.keySet());

        for (String name : allNames) {
            List<JavaSurface.Symbol> beforeSyms = beforeByName.getOrDefault(name, List.of());
            List<JavaSurface.Symbol> afterSyms = afterByName.getOrDefault(name, List.of());

            if (beforeSyms.size() <= 1 && afterSyms.size() <= 1) {
                // Simple case: at most one overload on each side.
                // Use existing name-based matching.
                JavaSurface.Symbol beforeSym = beforeSyms.isEmpty() ? null : beforeSyms.get(0);
                JavaSurface.Symbol afterSym = afterSyms.isEmpty() ? null : afterSyms.get(0);
                if (beforeSym == null && afterSym != null) {
                    addedList.add(new Added(afterSym));
                } else if (beforeSym != null && afterSym == null) {
                    removedList.add(new Removed(beforeSym));
                } else if (beforeSym != null && !beforeSym.equals(afterSym)) {
                    changedList.add(new SignatureChanged(beforeSym, afterSym));
                }
            } else {
                // Overloads: match by full identity (typeName + full signature).
                // First, remove exact before/after matches.
                Set<String> beforeFullKeys = beforeSyms.stream()
                        .map(DiffReport::fullKey)
                        .collect(Collectors.toSet());
                Set<String> afterFullKeys = afterSyms.stream()
                        .map(DiffReport::fullKey)
                        .collect(Collectors.toSet());

                // Collect unmatched symbols on each side.
                List<JavaSurface.Symbol> unmatchedBefore = beforeSyms.stream()
                        .filter(sym -> !afterFullKeys.contains(fullKey(sym)))
                        .collect(Collectors.toList());
                List<JavaSurface.Symbol> unmatchedAfter = afterSyms.stream()
                        .filter(sym -> !beforeFullKeys.contains(fullKey(sym)))
                        .collect(Collectors.toList());

                // If exactly one unmatched member on each side, report as SignatureChanged.
                if (unmatchedBefore.size() == 1 && unmatchedAfter.size() == 1) {
                    changedList.add(new SignatureChanged(unmatchedBefore.get(0), unmatchedAfter.get(0)));
                } else {
                    for (JavaSurface.Symbol sym : unmatchedBefore) {
                        removedList.add(new Removed(sym));
                    }
                    for (JavaSurface.Symbol sym : unmatchedAfter) {
                        addedList.add(new Added(sym));
                    }
                }
            }
        }

        return new DiffReport(removedList, addedList, changedList);
    }

    private static String fullKey(JavaSurface.Symbol symbol) {
        return symbol.typeName() + "#" + symbol.signature() + ":"
                + symbol.returnType() + ":" + symbol.visibility();
    }

    /**
     * Groups symbols by their simple member name (type name + method/field
     * name, with parameter list stripped), so overloads with the same name
     * share a bucket.
     */
    private static Map<String, List<JavaSurface.Symbol>> groupByName(JavaSurface surface) {
        Map<String, List<JavaSurface.Symbol>> byName = new LinkedHashMap<>();
        for (JavaSurface.Symbol symbol : surface.symbols()) {
            byName.computeIfAbsent(nameKey(symbol), k -> new ArrayList<>()).add(symbol);
        }
        return byName;
    }

    private static String nameKey(JavaSurface.Symbol symbol) {
        String signature = symbol.signature();
        int parenIndex = signature.indexOf('(');
        String memberName = parenIndex >= 0 ? signature.substring(0, parenIndex) : signature;
        return symbol.typeName() + "#" + memberName;
    }
}
