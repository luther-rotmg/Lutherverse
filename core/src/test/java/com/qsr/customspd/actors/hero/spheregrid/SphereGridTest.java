package com.qsr.customspd.actors.hero.spheregrid;

import com.watabou.utils.Bundle;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
 * EMBER_I(EMBER,1), EMBER_II(EMBER,1); every node costs 1 point.
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

		Bundle parent = new Bundle();
		parent.put("grid", g);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertTrue(Bundle.write(parent, out), "grid bundle must write");
		Bundle restored = Bundle.read(new ByteArrayInputStream(out.toByteArray()));
		SphereGrid g2 = (SphereGrid) restored.get("grid");

		assertNotNull(g2, "the grid must come back from the save");
		assertEquals(3, g2.unspentPoints());
		assertTrue(g2.isActivated(SphereNode.ATTUNEMENT));
		assertTrue(g2.isActivated(SphereNode.MIGHT_I));
		assertEquals(2, g2.mightLevel());
		assertFalse(g2.isActivated(SphereNode.EMBER_I), "un-activated nodes must stay off");
	}
}
