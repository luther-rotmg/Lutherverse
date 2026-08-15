package com.qsr.customspd.items;

import com.qsr.customspd.items.food.MysteryMeat;
import com.qsr.customspd.items.food.StewedMeat;
import com.qsr.customspd.items.potions.Potion;
import com.qsr.customspd.items.potions.PotionOfStrength;
import com.qsr.customspd.items.potions.elixirs.ElixirOfMight;
import com.qsr.customspd.items.quest.MetalShard;
import com.qsr.customspd.items.scrolls.Scroll;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Alchemy recipe consistency sweep (F1). Every {@link Recipe.SimpleRecipe} registered in
 * Recipe.java's static recipe lists must accept the exact ingredient list it declares
 * ({@code getIngredients()} builds it from {@code inputs}/{@code inQuantity}) and must be able
 * to instantiate its declared output. A recipe whose own declared inputs do not satisfy itself
 * is invisible in-game: the alchemy scene simply never offers it, and nothing else notices.
 * The sweep enumerates the same private static arrays {@code Recipe.findRecipes} consults, so
 * every future recipe is covered automatically.
 *
 * Identification strategy: {@code SimpleRecipe.testIngredients} rejects any unidentified
 * ingredient, so the sweep marks potions/scrolls identified via {@code anonymize()} — the
 * documented "always IDed, does not affect ID status" switch. It deliberately does NOT call
 * {@code identify()}: Potion.setKnown()/Scroll.setKnown() dereference {@code Dungeon.hero}
 * with no null check (fine in-game where a hero always exists, NPE headless), and
 * {@code handler.know()} would leak class-level identification into later tests. All other
 * ingredient classes used by simple recipes (catalysts, quest drops, food, spell components)
 * report {@code isIdentified() == true} unconditionally.
 */
class AlchemyRecipeSweepTest {

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
	}

	/**
	 * The four registries {@code Recipe.findRecipes} dispatches over. Read reflectively because
	 * they are private; a rename/restructure fails here loudly, forcing the sweep to follow.
	 */
	private static List<Recipe> allRegisteredRecipes() throws Exception {
		List<Recipe> all = new ArrayList<>();
		for (String fieldName : new String[]{
				"variableRecipes", "oneIngredientRecipes", "twoIngredientRecipes", "threeIngredientRecipes"}) {
			Field f = Recipe.class.getDeclaredField(fieldName);
			f.setAccessible(true);
			all.addAll(Arrays.asList((Recipe[]) f.get(null)));
		}
		return all;
	}

	/** Make one ingredient pass the isIdentified() gate without touching static handler state. */
	private static void identifyForAlchemy(Item ingredient) {
		if (ingredient instanceof Potion) {
			((Potion) ingredient).anonymize();
		} else if (ingredient instanceof Scroll) {
			((Scroll) ingredient).anonymize();
		} else if (!ingredient.isIdentified()) {
			ingredient.identify(false); // plain Item.identify: null-hero safe, instance-level
		}
	}

	@Test
	void everySimpleRecipeAcceptsItsOwnDeclaredIngredients() throws Exception {
		List<String> failures = new ArrayList<>();
		int totalRecipes = 0;
		int simpleRecipes = 0;

		// Nothing here should consume RNG, but item constructors are not under this suite's
		// control forever — pin a generator so the sweep can never go flaky.
		Random.pushGenerator(20260814L);
		try {
			for (Recipe recipe : allRegisteredRecipes()) {
				totalRecipes++;
				if (!(recipe instanceof Recipe.SimpleRecipe)) continue;
				simpleRecipes++;

				Recipe.SimpleRecipe simple = (Recipe.SimpleRecipe) recipe;
				String name = recipe.getClass().getName();
				try {
					ArrayList<Item> ingredients = simple.getIngredients();
					for (Item ingredient : ingredients) {
						identifyForAlchemy(ingredient);
					}

					if (!simple.testIngredients(ingredients)) {
						failures.add(name + ": testIngredients rejects the recipe's own declared ingredients");
						continue;
					}
					Item output = simple.sampleOutput(ingredients);
					if (output == null) {
						failures.add(name + ": sampleOutput is null (output class failed to instantiate)");
					} else if (output.quantity() < 1) {
						failures.add(name + ": sampleOutput quantity is " + output.quantity());
					}
					if (simple.cost(ingredients) <= 0) {
						failures.add(name + ": non-positive alchemy cost " + simple.cost(ingredients));
					}
				} catch (Throwable t) {
					failures.add(name + ": threw " + t);
				}
			}
		} finally {
			Random.popGenerator();
		}

		assertTrue(failures.isEmpty(), "mis-wired recipes:\n" + String.join("\n", failures));

		// Floors, not exact counts, so adding recipes stays free — their job is catching the
		// enumeration silently going empty (this repo's recurring gate failure) or a registry
		// restructure that strands the sweep. 37/26 are the counts at the time of writing.
		assertTrue(totalRecipes >= 37, "recipe registry shrank? saw only " + totalRecipes);
		assertTrue(simpleRecipes >= 26, "SimpleRecipe population shrank? saw only " + simpleRecipes);
	}

	@Test
	void unidentifiedIngredientIsRejected() {
		Recipe.SimpleRecipe recipe = new ElixirOfMight.Recipe();
		ArrayList<Item> ingredients = recipe.getIngredients(); // PotionOfStrength + AlchemicalCatalyst

		PotionOfStrength strength = null;
		for (Item i : ingredients) {
			if (i instanceof PotionOfStrength) strength = (PotionOfStrength) i;
		}
		assertNotNull(strength, "ElixirOfMight's declared inputs must include PotionOfStrength");

		// Potion identification is CLASS-level (a static ItemStatusHandler), and other suites
		// in this JVM identify PotionOfStrength as a side effect (HeroClass hero init identifies
		// config-listed starting items). Force it unknown deterministically — getKnown() returns
		// the live set — and restore in finally so nothing leaks either direction.
		boolean wasKnown = Potion.getKnown().remove(PotionOfStrength.class);
		try {
			assertFalse(strength.isIdentified(),
					"un-knowing the class must leave a plain instance unidentified");

			assertFalse(recipe.testIngredients(ingredients),
					"an unidentified ingredient must reject the whole set");

			strength.anonymize();
			assertTrue(recipe.testIngredients(ingredients),
					"identifying the potion is the only thing that was missing");
		} finally {
			if (wasKnown) Potion.getKnown().add(PotionOfStrength.class);
		}
	}

	@Test
	void underQuantityRejectedExactAndSplitStacksAccepted() {
		Recipe.SimpleRecipe twoMeat = new StewedMeat.twoMeat();

		ArrayList<Item> one = new ArrayList<>();
		one.add(new MysteryMeat().quantity(1));
		assertFalse(twoMeat.testIngredients(one), "1 of 2 required meat must not cook");

		ArrayList<Item> exact = new ArrayList<>();
		exact.add(new MysteryMeat().quantity(2));
		assertTrue(twoMeat.testIngredients(exact), "exactly the required quantity must cook");

		// needed[i] is decremented per matching ingredient, so a requirement may be satisfied
		// across separate stacks — that is how the alchemy scene's three item slots behave.
		ArrayList<Item> split = new ArrayList<>();
		split.add(new MysteryMeat().quantity(1));
		split.add(new MysteryMeat().quantity(1));
		assertTrue(twoMeat.testIngredients(split), "two 1-stacks must satisfy a 2-requirement");
	}

	@Test
	void overSupplyAcceptedAndBrewConsumesOnlyWhatIsNeeded() {
		Recipe.SimpleRecipe twoMeat = new StewedMeat.twoMeat();

		MysteryMeat meat = new MysteryMeat();
		meat.quantity(3);
		ArrayList<Item> ingredients = new ArrayList<>();
		ingredients.add(meat);

		// needed[i] just goes negative on over-supply; the recipe still matches...
		assertTrue(twoMeat.testIngredients(ingredients), "a 3-stack must satisfy a 2-requirement");

		// ...and brew() is what enforces fairness, consuming exactly inQuantity, not the stack.
		Item output = twoMeat.brew(ingredients);
		assertNotNull(output, "matching ingredients must brew");
		assertTrue(output instanceof StewedMeat, "twoMeat must output StewedMeat, got " + output);
		assertEquals(2, output.quantity(), "twoMeat declares outQuantity = 2 (one stew per meat)");
		assertEquals(1, meat.quantity(), "brew must consume exactly 2 of the 3 meat");
	}

	@Test
	void unmatchedExtraIngredientIsIgnoredButStillIdentityGated() {
		// CURRENT-BEHAVIOR PIN, not an endorsement: SimpleRecipe.testIngredients ignores
		// ingredients whose class is not in inputs[] (the inner loop simply never matches
		// them), so an alien item alongside a satisfied requirement does not reject the set.
		// In-game this is masked by Recipe.findRecipes dispatching on ingredient count. A
		// future strict-matching refactor should fail here and be a conscious decision.
		Recipe.SimpleRecipe twoMeat = new StewedMeat.twoMeat();

		ArrayList<Item> ingredients = new ArrayList<>();
		ingredients.add(new MysteryMeat().quantity(2));
		ingredients.add(new MetalShard()); // always identified, not a twoMeat input
		assertTrue(twoMeat.testIngredients(ingredients),
				"an unmatched identified extra is ignored under current semantics");

		// ...but the isIdentified() gate runs on EVERY list member, matching or not, so an
		// unidentified alien still rejects the whole set. Class-level knowledge is forced off
		// (and restored) because other suites in this JVM identify PotionOfStrength.
		boolean wasKnown = Potion.getKnown().remove(PotionOfStrength.class);
		try {
			PotionOfStrength unknown = new PotionOfStrength();
			assertFalse(unknown.isIdentified(),
					"un-knowing the class must leave a plain instance unidentified");
			ingredients.add(unknown);
			assertFalse(twoMeat.testIngredients(ingredients),
					"an unidentified item anywhere in the list rejects the set, even a non-input");
		} finally {
			if (wasKnown) Potion.getKnown().add(PotionOfStrength.class);
		}
	}
}
