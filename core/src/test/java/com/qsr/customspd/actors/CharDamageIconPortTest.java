package com.qsr.customspd.actors;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.actors.blobs.Electricity;
import com.qsr.customspd.actors.blobs.StormCloud;
import com.qsr.customspd.actors.blobs.ToxicGas;
import com.qsr.customspd.actors.buffs.AscensionChallenge;
import com.qsr.customspd.actors.buffs.Bleeding;
import com.qsr.customspd.actors.buffs.Burning;
import com.qsr.customspd.actors.buffs.Chill;
import com.qsr.customspd.actors.buffs.Corrosion;
import com.qsr.customspd.actors.buffs.Corruption;
import com.qsr.customspd.actors.buffs.Frost;
import com.qsr.customspd.actors.buffs.Hunger;
import com.qsr.customspd.actors.buffs.Ooze;
import com.qsr.customspd.actors.buffs.Poison;
import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.HeroSubClass;
import com.qsr.customspd.actors.mobs.DwarfKing;
import com.qsr.customspd.actors.mobs.Rat;
import com.qsr.customspd.effects.FloatingText;
import com.qsr.customspd.items.Heap;
import com.qsr.customspd.items.armor.glyphs.Viscosity;
import com.qsr.customspd.items.quest.Pickaxe;
import com.qsr.customspd.items.weapon.enchantments.Chilling;
import com.qsr.customspd.items.weapon.missiles.darts.Dart;
import com.qsr.customspd.levels.features.Chasm;
import com.qsr.customspd.levels.traps.DisintegrationTrap;
import com.qsr.customspd.levels.traps.GeyserTrap;
import com.qsr.customspd.sprites.CharSprite;
import com.qsr.customspd.test.HeadlessGdx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Char.damage picks the floating-text icon from the damage source (upstream 1c8b13fa6,
 * 9e32c1880, 3dc39de06, 5e075d80a). The sprite is a recording stub: no GL context headless,
 * and the icon id handed to showStatusWithIcon is the whole contract under test.
 */
class CharDamageIconPortTest {

	/** Records the icon instead of drawing it. */
	private static final class RecordingSprite extends CharSprite {
		int icon = Integer.MIN_VALUE;

		@Override
		public void showStatusWithIcon(int color, String text, int icon, Object... args) {
			this.icon = icon;
		}
	}

	private Hero prevHero;

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	@BeforeEach
	void saveStatics() {
		prevHero = Dungeon.hero;
		Dungeon.hero = null;
	}

	@AfterEach
	void restoreStatics() {
		Dungeon.hero = prevHero;
	}

	private static int iconFor(Object src) {
		Rat rat = new Rat();
		rat.HP = rat.HT = 100000; // never dies, never crosses HT/2
		RecordingSprite sprite = new RecordingSprite();
		rat.sprite = sprite;
		rat.damage(1, src);
		return sprite.icon;
	}

	@Test
	void physicalHitUsesThePlainDamageIcon() {
		assertEquals(FloatingText.PHYS_DMG, iconFor(new Rat()), "a melee attacker");
		assertEquals(FloatingText.PHYS_DMG, iconFor(new Chilling()),
				"the Chilling enchantment is not frost damage (5e075d80a switched the check to the Chill debuff)");
	}

	@Test
	void armorIgnoringPhysicalSourcesUseTheNoBlockIcon() {
		assertEquals(FloatingText.PHYS_DMG_NO_BLOCK, iconFor(new Chasm()), "falling into a chasm");
		assertEquals(FloatingText.PHYS_DMG_NO_BLOCK, iconFor(new Heap()), "wraith spawn blocked by a heap");
		assertEquals(FloatingText.PHYS_DMG_NO_BLOCK, iconFor(new DwarfKing.KingDamager()), "king's minion link");
		assertEquals(FloatingText.PHYS_DMG_NO_BLOCK, iconFor(new DwarfKing.Summoning()), "king's summoning circle");
	}

	@Test
	void sniperRangedHitsUseTheNoBlockIcon() {
		Hero hero = new Hero();
		Dungeon.hero = hero;
		hero.subClass = HeroSubClass.SNIPER;
		assertEquals(FloatingText.PHYS_DMG, iconFor(hero), "sniper in melee keeps the plain icon");

		hero.belongings.thrownWeapon = new Dart();
		assertEquals(FloatingText.PHYS_DMG_NO_BLOCK, iconFor(hero), "sniper ranged attacks ignore armor");

		hero.subClass = HeroSubClass.NONE;
		assertEquals(FloatingText.PHYS_DMG, iconFor(hero), "only the sniper subclass gets the special case");
	}

	@Test
	void magicAndPickaxeSources() {
		assertEquals(FloatingText.MAGIC_DMG, iconFor(new DisintegrationTrap()), "an AntiMagic.RESISTS source");
		assertEquals(FloatingText.PICK_DMG, iconFor(new Pickaxe()));
	}

	@Test
	void debuffAndEnvironmentSourcesUseTheirOwnIcons() {
		assertEquals(FloatingText.HUNGER,     iconFor(new Hunger()));
		assertEquals(FloatingText.BURNING,    iconFor(new Burning()));
		assertEquals(FloatingText.FROST,      iconFor(new Chill()));
		assertEquals(FloatingText.FROST,      iconFor(new Frost()));
		assertEquals(FloatingText.WATER,      iconFor(new GeyserTrap()));
		assertEquals(FloatingText.WATER,      iconFor(new StormCloud()));
		assertEquals(FloatingText.SHOCKING,   iconFor(new Electricity()));
		assertEquals(FloatingText.BLEEDING,   iconFor(new Bleeding()));
		assertEquals(FloatingText.TOXIC,      iconFor(new ToxicGas()));
		assertEquals(FloatingText.CORROSION,  iconFor(new Corrosion()));
		assertEquals(FloatingText.POISON,     iconFor(new Poison()));
		assertEquals(FloatingText.OOZE,       iconFor(new Ooze()));
		assertEquals(FloatingText.DEFERRED,   iconFor(new Viscosity.DeferedDamage()));
		assertEquals(FloatingText.CORRUPTION, iconFor(new Corruption()));
		assertEquals(FloatingText.AMULET,     iconFor(new AscensionChallenge()));
	}
}
