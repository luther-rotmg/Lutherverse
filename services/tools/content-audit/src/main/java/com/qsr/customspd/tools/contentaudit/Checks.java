package com.qsr.customspd.tools.contentaudit;

import java.util.ArrayList;
import java.util.List;

/** Runs the six completeness checks against one entity. */
public final class Checks {
    private Checks() {}

    public static List<Finding> run(EntityGraph.Entity e, SpriteIndex sprites,
                                    MessageIndex msgs, RegistryIndex reg) {
        List<Finding> findings = new ArrayList<>();
        String kind = e.kind();
        String name = e.cls().simpleName();
        String prefix = kind + " " + name + "#";

        if (kind.equals("Mob")) {
            if (!sprites.mobSpriteExists(e.resolvedSpriteClass())) {
                findings.add(new Finding(prefix + "M1",
                        "M1 sprite: spriteClass " + e.resolvedSpriteClass() + " -> asset PNG NOT FOUND"));
            }
            String base = "actors.mobs." + name.toLowerCase();
            if (!msgs.hasKey(base + ".name") || !msgs.hasKey(base + ".desc")) {
                findings.add(new Finding(prefix + "M2", "M2 localization: " + base + ".{name,desc} missing"));
            }
            if (!reg.bestiaryReferences(name)) {
                findings.add(new Finding(prefix + "M3", "M3 registration: not referenced in Bestiary.kt"));
            }
        } else { // Item
            if (!sprites.spriteExists(e.resolvedImageAsset())) {
                findings.add(new Finding(prefix + "I1",
                        "I1 sprite: image GeneralAsset." + e.resolvedImageAsset() + " -> PNG NOT FOUND"));
            }
            String base = itemKeyBase(e.cls());
            if (!msgs.hasKey(base + ".name") || !msgs.hasKey(base + ".desc")) {
                findings.add(new Finding(prefix + "I2", "I2 localization: " + base + ".{name,desc} missing"));
            }
            if (!reg.generatorReferences(name)) {
                findings.add(new Finding(prefix + "I3", "I3 registration: not referenced in Generator.java"));
            }
        }
        return findings;
    }

    /** items.<package-after-"com.qsr.customspd.items">.<lowercase name> */
    static String itemKeyBase(ContentClass c) {
        String pkg = c.packageName();
        String marker = "com.qsr.customspd.items";
        String tail = pkg.length() > marker.length() ? pkg.substring(marker.length() + 1) : "";
        String sub = tail.isEmpty() ? "" : tail + ".";
        return "items." + sub + c.simpleName().toLowerCase();
    }
}
