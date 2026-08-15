package com.qsr.customspd.actors.hero.spheregrid;

import com.qsr.customspd.test.SaveRoundtrip;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Play-tests the run-scoped Keybearer sphere grid at the logic level (no libGDX, no GUI):
 * point allocation, prerequisite gating, no double-spend, aggregate build levels, and the
 * save/restore roundtrip. The Bundle roundtrip is the highest-risk part — it is the actual
 * save path a real run would take, exercised here through Bundle.write/read.
 *
 * Magnitudes/costs asserted below track SphereNode: ATTUNEMENT(MIGHT,1), MIGHT_I(MIGHT,1),
 * EMBER_I(EMBER,1), EMBER_II(EMBER,1); tier-I/II nodes cost 1 point, tier-III nodes and
 * the ELEMENTAL_CONFLUX keystone cost 2.
 */
class SphereGridTest {

	@Test
	void freshGridIsEmpty() {
		SphereGrid g = new SphereGrid();
		assertEquals(0, g.unspentPoints());
		assertEquals(0, g.emberLevel());
		assertEquals(0, g.mightLevel());
		assertEquals(0, g.vigorLevel());
		assertFalse(g.isActivated(SphereNode.ATTUNEMENT));
	}

	@Test
	void cannotActivateWithoutPoints() {
		SphereGrid g = new SphereGrid();
		assertFalse(g.canActivate(SphereNode.ATTUNEMENT));
		assertFalse(g.activate(SphereNode.ATTUNEMENT));
		assertFalse(g.isActivated(SphereNode.ATTUNEMENT));
	}

	@Test
	void cannotActivateWithoutPrerequisite() {
		SphereGrid g = new SphereGrid();
		g.grantPoints(5);
		// EMBER_I requires ATTUNEMENT, which is not yet active.
		assertFalse(g.prerequisiteMet(SphereNode.EMBER_I));
		assertFalse(g.canActivate(SphereNode.EMBER_I));
		assertFalse(g.activate(SphereNode.EMBER_I));
		assertEquals(5, g.unspentPoints()); // nothing spent on a blocked node
	}

	@Test
	void activatingSpendsAPointAndAppliesEffect() {
		SphereGrid g = new SphereGrid();
		g.grantPoints(2);
		assertTrue(g.activate(SphereNode.ATTUNEMENT));
		assertTrue(g.isActivated(SphereNode.ATTUNEMENT));
		assertEquals(1, g.unspentPoints());
		assertEquals(1, g.mightLevel()); // ATTUNEMENT is a MIGHT node, magnitude 1
	}

	@Test
	void prerequisiteChainUnlocksDeeperNodesAndSumsEffect() {
		SphereGrid g = new SphereGrid();
		g.grantPoints(3);
		assertTrue(g.activate(SphereNode.ATTUNEMENT));
		assertTrue(g.canActivate(SphereNode.EMBER_I));
		assertTrue(g.activate(SphereNode.EMBER_I));
		assertTrue(g.activate(SphereNode.EMBER_II));
		assertEquals(2, g.emberLevel()); // EMBER_I(1) + EMBER_II(1)
		assertEquals(0, g.unspentPoints());
	}

	@Test
	void cannotReactivateOrDoubleSpend() {
		SphereGrid g = new SphereGrid();
		g.grantPoints(3);
		assertTrue(g.activate(SphereNode.ATTUNEMENT));
		int after = g.unspentPoints();
		assertFalse(g.canActivate(SphereNode.ATTUNEMENT));
		assertFalse(g.activate(SphereNode.ATTUNEMENT));
		assertEquals(after, g.unspentPoints());
	}

	@Test
	void survivesBundleRoundtrip() throws IOException {
		SphereGrid g = new SphereGrid();
		g.grantPoints(5);
		assertTrue(g.activate(SphereNode.ATTUNEMENT));
		assertTrue(g.activate(SphereNode.MIGHT_I));
		// 2 spent, 3 left; mightLevel = ATTUNEMENT(1) + MIGHT_I(1) = 2

		SphereGrid g2 = SaveRoundtrip.of(g);

		assertNotNull(g2, "the grid must come back from the save");
		assertEquals(3, g2.unspentPoints());
		assertTrue(g2.isActivated(SphereNode.ATTUNEMENT));
		assertTrue(g2.isActivated(SphereNode.MIGHT_I));
		assertEquals(2, g2.mightLevel());
		assertFalse(g2.isActivated(SphereNode.EMBER_I), "un-activated nodes must stay off");
	}

	@Test
	void fullyActivatedGridSurvivesRoundtrip() throws IOException {
		// Every node activated: the stress case for serialization (all activations + 0 points).
		SphereGrid g = new SphereGrid();
		int totalCost = 0;
		for (SphereNode node : SphereNode.values()) {
			totalCost += node.cost;
		}
		g.grantPoints(totalCost);
		for (SphereNode node : SphereNode.values()) {
			assertTrue(g.activate(node), "every node should be reachable/affordable here: " + node);
		}
		int ember = g.emberLevel(), frost = g.frostLevel(), storm = g.stormLevel();
		int might = g.mightLevel(), vigor = g.vigorLevel(), ability = g.abilityLevel();

		SphereGrid g2 = SaveRoundtrip.of(g);

		assertNotNull(g2);
		for (SphereNode node : SphereNode.values()) {
			assertTrue(g2.isActivated(node), "every activated node must survive the save: " + node);
		}
		assertEquals(0, g2.unspentPoints());
		assertEquals(ember, g2.emberLevel());
		assertEquals(frost, g2.frostLevel());
		assertEquals(storm, g2.stormLevel());
		assertEquals(might, g2.mightLevel());
		assertEquals(vigor, g2.vigorLevel());
		assertEquals(ability, g2.abilityLevel());
	}

	@Test
	void stormBranchSumsThroughItsTierThree() {
		SphereGrid g = new SphereGrid();
		g.grantPoints(5); // ATTUNEMENT(1) + STORM_I(1) + STORM_II(1) + TEMPEST(2)
		assertTrue(g.activate(SphereNode.ATTUNEMENT));
		assertTrue(g.activate(SphereNode.STORM_I));
		assertEquals(1, g.stormLevel());
		assertTrue(g.activate(SphereNode.STORM_II));
		assertEquals(3, g.stormLevel()); // STORM_I(1) + STORM_II(2)
		assertTrue(g.activate(SphereNode.TEMPEST));
		assertEquals(5, g.stormLevel()); // + TEMPEST(2)
		assertEquals(0, g.unspentPoints());
	}

	@Test
	void keystoneNeedsAllThreeElementBranches() {
		SphereGrid g = new SphereGrid();
		g.grantPoints(20);
		assertTrue(g.activate(SphereNode.ATTUNEMENT));
		assertTrue(g.activate(SphereNode.EMBER_I));
		assertTrue(g.activate(SphereNode.EMBER_II));
		assertTrue(g.activate(SphereNode.FROST_I));
		assertTrue(g.activate(SphereNode.FROST_II));
		// Two of three element branches deep — the keystone must stay locked.
		assertFalse(g.prerequisiteMet(SphereNode.ELEMENTAL_CONFLUX));
		assertFalse(g.activate(SphereNode.ELEMENTAL_CONFLUX));
		assertTrue(g.activate(SphereNode.STORM_I));
		assertTrue(g.activate(SphereNode.STORM_II));
		// All three — now it opens.
		assertTrue(g.prerequisiteMet(SphereNode.ELEMENTAL_CONFLUX));
		assertTrue(g.activate(SphereNode.ELEMENTAL_CONFLUX));
	}

	@Test
	void confluxKeystoneRaisesEveryElementByOne() {
		SphereGrid g = new SphereGrid();
		g.grantPoints(20);
		for (SphereNode n : new SphereNode[]{SphereNode.ATTUNEMENT,
				SphereNode.EMBER_I, SphereNode.EMBER_II,
				SphereNode.FROST_I, SphereNode.FROST_II,
				SphereNode.STORM_I, SphereNode.STORM_II}) {
			assertTrue(g.activate(n));
		}
		int ember = g.emberLevel(), frost = g.frostLevel(), storm = g.stormLevel();
		int ability = g.abilityLevel();
		assertTrue(g.activate(SphereNode.ELEMENTAL_CONFLUX));
		assertEquals(ember + 1, g.emberLevel(), "conflux: +1 ember");
		assertEquals(frost + 1, g.frostLevel(), "conflux: +1 frost");
		assertEquals(storm + 1, g.stormLevel(), "conflux: +1 storm");
		assertEquals(ability, g.abilityLevel(), "conflux (magnitude 0) must not inflate ability");
	}
}
