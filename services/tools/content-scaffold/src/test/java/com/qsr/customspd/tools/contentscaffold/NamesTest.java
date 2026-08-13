package com.qsr.customspd.tools.contentscaffold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NamesTest {
    @Test
    void derivesFromPascalCase() {
        Names n = Names.of("SewerCrab");
        assertEquals("SewerCrab", n.className());
        assertEquals("sewer_crab", n.snake());       // asset filename + GeneralAsset base
        assertEquals("SEWER_CRAB", n.upperSnake());   // GeneralAsset member
        assertEquals("sewercrab", n.lower());          // message key leaf (matches content-audit)
        assertEquals("sprites/chars/sewer_crab.png", n.mobAssetPath());
        assertEquals("sprites/items/sewer_crab.png", n.itemAssetPath());
    }

    @Test
    void singleWordName() {
        Names n = Names.of("Wisp");
        assertEquals("wisp", n.snake());
        assertEquals("WISP", n.upperSnake());
        assertEquals("wisp", n.lower());
    }
}
