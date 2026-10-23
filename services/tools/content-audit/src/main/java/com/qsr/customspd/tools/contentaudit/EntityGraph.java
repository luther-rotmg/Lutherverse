package com.qsr.customspd.tools.contentaudit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Builds the extends-ancestry graph, enumerates concrete Mob/Item entities,
 *  and resolves sprite/image touchpoints through inheritance. */
public final class EntityGraph {
    private static final Set<String> BASE_TYPES = Set.of(
            "Mob", "Item", "MeleeWeapon", "MissileWeapon", "Armor", "Wand",
            "Ring", "Artifact", "Potion", "Scroll", "Food");

    private EntityGraph() {}

    public record Entity(String kind, ContentClass cls,
                         String resolvedSpriteClass, String resolvedImageAsset) {}

    /** A simple name declared by more than one parsed class, with the package-qualified
     *  declarations and the concrete classes {@link #build} drops because their ancestry
     *  passes through it. */
    public record Ambiguity(String simpleName, List<String> declarations, List<String> excluded) {}

    public static List<Ambiguity> ambiguities(List<ContentClass> all) {
        Map<String, List<String>> decls = new TreeMap<>();
        for (ContentClass c : all) {
            decls.computeIfAbsent(c.simpleName(), k -> new ArrayList<>()).add(qualified(c));
        }
        decls.values().removeIf(d -> d.size() < 2);
        if (decls.isEmpty()) return List.of();

        Map<String, ContentClass> byName = uniqueByName(all);
        Map<String, List<String>> excluded = new TreeMap<>();
        for (ContentClass c : all) {
            if (c.isAbstract() || BASE_TYPES.contains(c.simpleName())) continue;
            ContentClass cur = c;
            int guard = 0;
            while (cur != null && cur.superSimpleName() != null && guard++ < 50) {
                if (decls.containsKey(cur.superSimpleName())) {
                    excluded.computeIfAbsent(cur.superSimpleName(), k -> new ArrayList<>()).add(qualified(c));
                    break;
                }
                cur = byName.get(cur.superSimpleName());
            }
        }

        List<Ambiguity> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : decls.entrySet()) {
            List<String> d = new ArrayList<>(e.getValue());
            List<String> x = new ArrayList<>(excluded.getOrDefault(e.getKey(), List.of()));
            Collections.sort(d);
            Collections.sort(x);
            out.add(new Ambiguity(e.getKey(), d, x));
        }
        return out;
    }

    public static List<Entity> build(List<ContentClass> all) {
        Map<String, ContentClass> byName = uniqueByName(all);

        List<Entity> out = new ArrayList<>();
        for (ContentClass c : all) {
            if (c.isAbstract() || BASE_TYPES.contains(c.simpleName())) continue;
            String root = ancestryRoot(c, byName); // "Mob", "Item", or null
            if (root == null) continue;
            out.add(new Entity(root, c,
                    resolve(c, byName, /*sprite=*/true),
                    resolve(c, byName, /*sprite=*/false)));
        }
        return out;
    }

    private static Map<String, ContentClass> uniqueByName(List<ContentClass> all) {
        Map<String, ContentClass> byName = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (ContentClass c : all) {
            if (!seen.add(c.simpleName())) {
                // Duplicate simple name across packages: an unqualified `extends X`
                // elsewhere can't tell which one it means, so don't silently guess.
                // ambiguities() reports what this drops.
                byName.remove(c.simpleName());
                continue;
            }
            byName.put(c.simpleName(), c);
        }
        return byName;
    }

    private static String qualified(ContentClass c) {
        return c.packageName().isEmpty() ? c.simpleName() : c.packageName() + "." + c.simpleName();
    }

    /** Walks up superclasses; returns "Mob" or "Item" if the ancestry reaches one, else null. */
    private static String ancestryRoot(ContentClass c, Map<String, ContentClass> byName) {
        ContentClass cur = c;
        int guard = 0;
        while (cur != null && guard++ < 50) {
            if ("Mob".equals(cur.simpleName())) return "Mob";
            if ("Item".equals(cur.simpleName())) return "Item";
            cur = cur.superSimpleName() == null ? null : byName.get(cur.superSimpleName());
        }
        return null;
    }

    /** Nearest assigned spriteClass (sprite=true) or imageAsset (sprite=false) in the ancestry. */
    private static String resolve(ContentClass c, Map<String, ContentClass> byName, boolean sprite) {
        ContentClass cur = c;
        int guard = 0;
        while (cur != null && guard++ < 50) {
            String v = sprite ? cur.spriteClass() : cur.imageAsset();
            if (v != null) return v;
            cur = cur.superSimpleName() == null ? null : byName.get(cur.superSimpleName());
        }
        return null;
    }
}
