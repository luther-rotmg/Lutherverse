package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TemplatesTest {
    private final Names wisp = Names.of("Wisp");

    @Test
    void mobClassHasShapeAndTodo() {
        String s = Templates.mobClass(wisp);
        assertTrue(s.contains("package com.qsr.customspd.actors.mobs;"));
        assertTrue(s.contains("public class Wisp extends Mob {"));
        assertTrue(s.contains("spriteClass = WispSprite.class;"));
        assertTrue(s.contains("// TODO"));
    }

    @Test
    void mobSpriteTexturesTheAsset() {
        String s = Templates.mobSprite(wisp);
        assertTrue(s.contains("public class WispSprite extends MobSprite {"));
        assertTrue(s.contains("GeneralAsset.WISP"));
    }

    @Test
    void itemClassHasImageAndTodo() {
        String s = Templates.itemClass(wisp);
        assertTrue(s.contains("public class Wisp extends Item {"));
        assertTrue(s.contains("image = GeneralAsset.WISP;"));
        assertTrue(s.contains("// TODO"));
    }

    @Test
    void assetAndMessageLines() {
        assertTrue(Templates.generalAssetLine(wisp, true).equals("    WISP(\"sprites/chars/wisp.png\"),"));
        assertTrue(Templates.messageLines(wisp, true).contains("actors.mobs.wisp.name="));
        assertTrue(Templates.messageLines(wisp, false).contains("items.wisp.name="));
    }
}
