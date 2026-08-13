package com.qsr.customspd.tools.contentaudit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ContentClassParserTest {
    @Test
    void extractsMobWithInheritedSpriteInInitializer() {
        String src = "package com.qsr.customspd.actors.mobs;\n"
                + "public class Rat extends Mob {\n"
                + "  { spriteClass = RatSprite.class; }\n"
                + "}\n";
        ContentClass c = ContentClassParser.parse(src);
        assertEquals("Rat", c.simpleName());
        assertEquals("com.qsr.customspd.actors.mobs", c.packageName());
        assertEquals("Mob", c.superSimpleName());
        assertFalse(c.isAbstract());
        assertEquals("RatSprite", c.spriteClass());
        assertNull(c.imageAsset());
    }

    @Test
    void extractsItemImageAsset() {
        String src = "package com.qsr.customspd.items.food;\n"
                + "public class SupplyRation extends Food {\n"
                + "  { image = GeneralAsset.SUPPLY_RATION; }\n"
                + "}\n";
        ContentClass c = ContentClassParser.parse(src);
        assertEquals("SupplyRation", c.simpleName());
        assertEquals("Food", c.superSimpleName());
        assertEquals("SUPPLY_RATION", c.imageAsset());
        assertNull(c.spriteClass());
    }

    @Test
    void marksAbstractClasses() {
        ContentClass c = ContentClassParser.parse("public abstract class Mob extends Char {}");
        assertEquals("Mob", c.simpleName());
        // abstract classes are enumerated but skipped by EntityGraph
        org.junit.jupiter.api.Assertions.assertTrue(c.isAbstract());
    }

    @Test
    void returnsNullForNoTopLevelClass() {
        assertNull(ContentClassParser.parse("package x;\n"));
    }

    @Test
    void skipsLeadingInterfaceToFindTheClass() {
        String src = "package com.qsr.customspd.actors.mobs;\n"
                + "public interface Flying {}\n"
                + "public class Bat extends Mob {}\n";
        ContentClass c = ContentClassParser.parse(src);
        assertEquals("Bat", c.simpleName());
        assertEquals("Mob", c.superSimpleName());
    }

    @Test
    void returnsNullForUnparseableSource() {
        assertNull(ContentClassParser.parse("class {"));
    }
}
