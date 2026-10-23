package com.qsr.customspd.items.remains;

import com.qsr.customspd.actors.hero.HeroClass;
import com.qsr.customspd.assets.GeneralAsset;
import com.qsr.customspd.test.HeadlessGdx;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The v2.3.0 class-based remains items (upstream f6054cc89). Bones hands a dead hero's class to
 * {@link RemainsItem#get}; a wrong mapping would silently give every class the wrong effect, and
 * a class missing from the switch must still yield an item rather than null (CPDU's extra
 * KEYBEARER class rides upstream's WARRIOR/default arm).
 */
class RemainsItemTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@Test
	void eachHeroClassMapsToItsUpstreamRemainsItem() {
		Map<HeroClass, Class<? extends RemainsItem>> expected = new LinkedHashMap<>();
		expected.put(HeroClass.WARRIOR, SealShard.class);
		expected.put(HeroClass.MAGE, BrokenStaff.class);
		expected.put(HeroClass.ROGUE, CloakScrap.class);
		expected.put(HeroClass.HUNTRESS, BowFragment.class);
		expected.put(HeroClass.DUELIST, BrokenHilt.class);
		// not an upstream class: falls through upstream's `case WARRIOR: default:` arm
		expected.put(HeroClass.KEYBEARER, SealShard.class);

		for (HeroClass cls : HeroClass.values()) {
			assertTrue(expected.containsKey(cls), "test must cover every hero class, missing " + cls);
			RemainsItem item = RemainsItem.get(cls);
			assertNotNull(item, cls + " must always yield a remains item");
			assertEquals(expected.get(cls), item.getClass(), cls + " remains item");
		}
	}

	@Test
	void remainsItemsAreWiredForSpriteAndText() {
		Map<Class<? extends RemainsItem>, GeneralAsset> sprites = new LinkedHashMap<>();
		sprites.put(SealShard.class, GeneralAsset.SEAL_SHARD);
		sprites.put(BrokenStaff.class, GeneralAsset.BROKEN_STAFF);
		sprites.put(CloakScrap.class, GeneralAsset.CLOAK_SCRAP);
		sprites.put(BowFragment.class, GeneralAsset.BOW_FRAGMENT);
		sprites.put(BrokenHilt.class, GeneralAsset.BROKEN_HILT);

		for (HeroClass cls : HeroClass.values()) {
			RemainsItem item = RemainsItem.get(cls);
			assertEquals(sprites.get(item.getClass()), item.image(), item.getClass().getSimpleName() + " sprite");
			assertFalse(item.name().contains("NO TEXT FOUND"), item.getClass().getSimpleName() + " name key");
			assertFalse(item.desc().contains("NO TEXT FOUND"), item.getClass().getSimpleName() + " desc key");
			assertTrue(item.isIdentified());
			assertFalse(item.bones, "remains items must never be picked as bones themselves");
			assertEquals(RemainsItem.AC_USE, item.defaultAction());
			assertEquals(50, item.value());
		}
	}
}
