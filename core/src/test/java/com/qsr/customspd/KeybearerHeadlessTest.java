package com.qsr.customspd;

import com.qsr.customspd.items.weapon.melee.MeleeWeapon;
import com.qsr.customspd.modding.HeroConfig;
import com.qsr.customspd.modding.JsonConfigRetriever;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F1 integration test: exercises the real hero-config asset path (ModManager + Gdx.files) on the
 * headless harness to verify the Keybearer starting kit is wired end to end — the keybearer.json
 * asset exists, parses into a HeroConfig, and equips the Keyblade. This is runtime wiring a plain
 * unit test cannot reach (it needs Gdx.files), and it directly catches a real prototype risk: a
 * wrong asset path or weapon type in the kit.
 */
class KeybearerHeadlessTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@Test
	void keybearerKitLoadsAndEquipsTheKeyblade() {
		HeroConfig config = JsonConfigRetriever.INSTANCE.retrieveHeroConfig("keybearer");
		assertNotNull(config, "keybearer.json must load through the real asset path");
		assertNotNull(config.getWeapon(), "the Keybearer kit must define a starting weapon");
		assertEquals("weapon.melee.Keyblade", config.getWeapon().getType(),
				"the Keybearer must start with the Keyblade");
	}

	@Test
	void keybladeClassInstantiatesViaTheKitReflectionPath() {
		// HeroClass.setUp builds the kit with Reflection.newInstance(Reflection.forName("com.qsr.customspd.items." + type)).
		// This proves that path resolves to a real, constructible MeleeWeapon — a wrong/renamed
		// class or a throwing constructor would surface here, not just at runtime on class-select.
		Object weapon = Reflection.newInstance(
				Reflection.forName("com.qsr.customspd.items." + "weapon.melee.Keyblade"));
		assertNotNull(weapon, "the Keyblade class must exist and be constructible");
		assertTrue(weapon instanceof MeleeWeapon, "the Keyblade must be a MeleeWeapon");
	}
}
