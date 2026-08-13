package com.qsr.customspd.tools.contentaudit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds the extends-ancestry graph, enumerates concrete Mob/Item entities,
 *  and resolves sprite/image touchpoints through inheritance. */
public final class EntityGraph {
    private static final Set<String> BASE_TYPES = Set.of(
            "Mob", "Item", "MeleeWeapon", "MissileWeapon", "Armor", "Wand",
            "Ring", "Artifact", "Potion", "Scroll", "Food");

    private EntityGraph() {}

    public record Entity(String kind, ContentClass cls,
                         String resolvedSpriteClass, String resolvedImageAsset) {}

    public static List<Entity> build(List<ContentClass> all) {
        Map<String, ContentClass> byName = new HashMap<>();
        for (ContentClass c : all) byName.put(c.simpleName(), c);

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
