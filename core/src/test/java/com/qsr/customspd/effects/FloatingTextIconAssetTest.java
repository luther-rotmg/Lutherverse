package com.qsr.customspd.effects;

import com.qsr.customspd.assets.GeneralAsset;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Upstream (1c8b13fa6 / 9e32c1880 / 3dc39de06) draws FloatingText icons from one
 * text_icons.png sheet by index. CPDU ships one PNG per icon under effects/text_icons/ and maps
 * each index to a GeneralAsset (cpdu-cxr). An index with no asset, or an asset whose file is
 * missing, only shows up at runtime as a crash the first time that icon is drawn, so pin both.
 */
class FloatingTextIconAssetTest {

	/** core:test runs with src/main/assets as its working directory (see HeadlessGdx). */
	private static final File ASSETS = new File(".");

	/** Every public static int icon index FloatingText declares, by field name. */
	private static Map<String, Integer> iconConstants() throws IllegalAccessException {
		Map<String, Integer> icons = new LinkedHashMap<>();
		for (Field f : FloatingText.class.getDeclaredFields()) {
			int m = f.getModifiers();
			// ICON_SIZE is the only final int; the icon indices are non-final as upstream has them
			if (f.getType() == int.class && Modifier.isPublic(m) && Modifier.isStatic(m) && !Modifier.isFinal(m)
					&& !f.getName().equals("NO_ICON")) {
				icons.put(f.getName(), f.getInt(null));
			}
		}
		return icons;
	}

	@Test
	void everyIconConstantResolvesToAnExistingSevenPixelPng() throws Exception {
		assertTrue(new File(ASSETS, GeneralAsset.STAR.getPath()).isFile(),
				"fixture sanity: working directory must be core/src/main/assets, was " + ASSETS.getAbsolutePath());

		Map<String, Integer> icons = iconConstants();
		// the 23 icons the four upstream commits end with (PHYS_DMG..ENERGY, indices 4 and 22 unused)
		assertEquals(23, icons.size(), "icon constants found: " + icons.keySet());

		Map<GeneralAsset, String> seen = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> e : icons.entrySet()) {
			GeneralAsset asset = FloatingText.iconAsset(e.getValue());
			assertNotNull(asset, "FloatingText." + e.getKey() + " (" + e.getValue() + ") has no GeneralAsset");

			String clash = seen.put(asset, e.getKey());
			if (clash != null) fail(e.getKey() + " and " + clash + " both map to " + asset);

			File png = new File(ASSETS, asset.getPath());
			assertTrue(png.isFile(), "FloatingText." + e.getKey() + " -> " + asset + " -> missing file " + asset.getPath());

			BufferedImage img = ImageIO.read(png);
			assertNotNull(img, asset.getPath() + " is not a readable image");
			assertEquals(FloatingText.ICON_SIZE, img.getWidth(), asset.getPath() + " width");
			assertEquals(FloatingText.ICON_SIZE, img.getHeight(), asset.getPath() + " height");

			boolean opaquePixel = false;
			for (int y = 0; y < img.getHeight() && !opaquePixel; y++) {
				for (int x = 0; x < img.getWidth() && !opaquePixel; x++) {
					opaquePixel = (img.getRGB(x, y) >>> 24) != 0;
				}
			}
			assertTrue(opaquePixel, asset.getPath() + " is fully transparent - sliced from an empty sheet cell?");
		}
	}

	@Test
	void unusedSheetIndicesHaveNoAsset() {
		assertNull(FloatingText.iconAsset(FloatingText.NO_ICON));
		// indices 4 and 22 are blank cells in upstream's sheet; nothing should claim them
		assertNull(FloatingText.iconAsset(4));
		assertNull(FloatingText.iconAsset(22));
	}
}
