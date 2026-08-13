/*
 * Lutherverse
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.qsr.customspd.windows;

import com.qsr.customspd.actors.hero.Hero;
import com.qsr.customspd.actors.hero.spheregrid.SphereGrid;
import com.qsr.customspd.actors.hero.spheregrid.SphereNode;
import com.qsr.customspd.scenes.GameScene;
import com.qsr.customspd.scenes.PixelScene;
import com.qsr.customspd.ui.RedButton;
import com.qsr.customspd.ui.RenderedTextBlock;
import com.qsr.customspd.ui.Window;

/**
 * Prototype sphere-grid screen for the Keybearer (build-craft spine).
 *
 * A deliberately plain list UI: shows unspent points and every node as a button you
 * can activate (spending a point) when its prerequisite is met and you have a point.
 * Activated nodes are marked and disabled; locked nodes are disabled with a hint.
 * On each activation the window rebuilds itself (hide + reopen). The proper
 * branching-web visual is a later increment — this proves the spend loop.
 */
public class WndSphereGrid extends Window {

	private static final int WIDTH = 150;
	private static final float GAP = 2;
	private static final float BTN_H = 16;

	private final Hero hero;

	public WndSphereGrid(Hero hero) {
		super();
		this.hero = hero;

		final SphereGrid grid = hero.sphereGrid;

		RenderedTextBlock title = PixelScene.renderTextBlock(9);
		title.text("Sphere Grid");
		title.setPos((WIDTH - title.width()) / 2f, 0);
		add(title);

		RenderedTextBlock points = PixelScene.renderTextBlock(7);
		points.text("Unspent points: _" + (grid == null ? 0 : grid.unspentPoints()) + "_");
		points.setPos(0, title.bottom() + 2 * GAP);
		add(points);

		float pos = points.bottom() + 3 * GAP;

		if (grid != null) {
			for (final SphereNode node : SphereNode.values()) {
				final boolean activated = grid.isActivated(node);
				final boolean canAct = grid.canActivate(node);

				RedButton btn = new RedButton(nodeLabel(node, grid, activated), 6) {
					@Override
					protected void onClick() {
						if (grid.activate(node)) {
							//Apply HT-affecting nodes (Vigor) immediately; heals by the gain.
							WndSphereGrid.this.hero.updateHT(true);
							hide();
							GameScene.show(new WndSphereGrid(WndSphereGrid.this.hero));
						}
					}
				};
				btn.leftJustify = true;
				btn.setRect(0, pos, WIDTH, BTN_H);
				btn.enable(canAct);
				add(btn);

				pos = btn.bottom() + GAP;
			}
		}

		RedButton close = new RedButton("Close") {
			@Override
			protected void onClick() {
				hide();
			}
		};
		close.setRect(0, pos + GAP, WIDTH, 18);
		add(close);

		resize(WIDTH, (int) close.bottom());
	}

	private static String nodeLabel(SphereNode node, SphereGrid grid, boolean activated) {
		String eff = "+" + node.magnitude + " " + effectName(node.effect);
		if (activated) {
			return "_" + node.name() + "_  (" + eff + ")  ✓";
		}
		if (!grid.prerequisiteMet(node)) {
			return node.name() + "  (" + eff + ")  — needs " + node.requires;
		}
		return node.name() + "  (" + eff + ", " + node.cost + "pt)";
	}

	private static String effectName(SphereNode.Effect e) {
		switch (e) {
			case EMBER: return "Ember (fire)";
			case MIGHT: return "Might (dmg)";
			case VIGOR: return "Vigor (hp)";
			default:    return e.name();
		}
	}
}
