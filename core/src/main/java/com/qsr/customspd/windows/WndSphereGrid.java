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
import com.qsr.customspd.actors.hero.spheregrid.SphereGridProgress;
import com.qsr.customspd.actors.hero.spheregrid.SphereNode;
import com.qsr.customspd.scenes.GameScene;
import com.qsr.customspd.scenes.PixelScene;
import com.qsr.customspd.ui.RedButton;
import com.qsr.customspd.ui.RenderedTextBlock;
import com.qsr.customspd.ui.ScrollPane;
import com.qsr.customspd.ui.Window;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;

/**
 * Prototype sphere-grid screen for the Keybearer (build-craft spine) — the hybrid grid's UI.
 *
 * Two layers meet here. Every node is one of:
 *  - LOCKED   -> spend persistent {@code Insight} to unlock it forever ({@link SphereGridProgress})
 *  - UNLOCKED -> spend a run-scoped point to activate it this descent ({@link SphereGrid})
 *  - ACTIVE   -> marked, done for the run
 * The header shows both currencies. The window rebuilds itself (hide + reopen) after each action.
 * The proper branching-web visual is a later increment; this proves the unlock/allocate loop.
 */
public class WndSphereGrid extends Window {

	private static final int WIDTH = 168;
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

		RenderedTextBlock status = PixelScene.renderTextBlock(7);
		status.text("Insight: _" + SphereGridProgress.insight() + "_    Points: _"
				+ (grid == null ? 0 : grid.unspentPoints()) + "_");
		status.setPos(0, title.bottom() + 2 * GAP);
		add(status);

		// The node list lives in a ScrollPane clamped to the ui camera, so a growing grid
		// (keystones, tier-IIIs, future branches) can never push nodes — or the close
		// button — off-screen. Buttons inside a ScrollPane don't receive raw touches;
		// the pane forwards clicks to them (the WndKeyBindings idiom).
		final ArrayList<RedButton> nodeButtons = new ArrayList<>();
		final ArrayList<SphereNode> nodeOrder = new ArrayList<>();

		Component listContent = new Component();
		ScrollPane list = new ScrollPane(listContent) {
			@Override
			public void onClick(float x, float y) {
				for (int i = 0; i < nodeButtons.size(); i++) {
					RedButton btn = nodeButtons.get(i);
					if (btn.active && y >= btn.top() && y < btn.bottom()) {
						nodeClicked(nodeOrder.get(i));
						break;
					}
				}
			}
		};
		add(list);

		float pos = 0;
		if (grid != null) {
			for (final SphereNode node : SphereNode.values()) {
				final boolean activated = grid.isActivated(node);
				final boolean unlocked = SphereGridProgress.isUnlocked(node);
				final boolean canUnlock = SphereGridProgress.canUnlock(node);
				final boolean canActivate = unlocked && grid.canActivate(node);

				RedButton btn = new RedButton(nodeLabel(node, grid, activated, unlocked), 6);
				btn.leftJustify = true;
				btn.setRect(0, pos, WIDTH, BTN_H);
				btn.enable(!activated && (canUnlock || canActivate));
				listContent.add(btn);
				nodeButtons.add(btn);
				nodeOrder.add(node);

				pos = btn.bottom() + GAP;
			}
		}
		listContent.setSize(WIDTH, pos);

		RedButton close = new RedButton("Close") {
			@Override
			protected void onClick() {
				hide();
			}
		};

		float listTop = status.bottom() + 3 * GAP;
		float closeH = 18;
		// Clamp the whole window to the ui camera; the list absorbs the squeeze.
		float maxWindowH = PixelScene.uiCamera.height - 20;
		float listH = Math.min(pos, maxWindowH - listTop - closeH - 2 * GAP);
		list.setRect(0, listTop, WIDTH, listH);
		close.setRect(0, list.bottom() + GAP, WIDTH, closeH);
		add(close);

		resize(WIDTH, (int) close.bottom());
	}

	private void nodeClicked(SphereNode node) {
		SphereGrid grid = hero.sphereGrid;
		boolean changed;
		if (!SphereGridProgress.isUnlocked(node)) {
			changed = SphereGridProgress.unlock(node); // spend Insight (persistent)
		} else {
			changed = grid != null && grid.activate(node); // spend a point (this run)
			if (changed) hero.updateHT(true); // apply Vigor immediately
		}
		if (changed) {
			hide();
			GameScene.show(new WndSphereGrid(hero));
		}
	}

	private static String nodeLabel(SphereNode node, SphereGrid grid, boolean activated, boolean unlocked) {
		// Keystones implement their effect in SphereGrid's accessors (magnitude 0), so a
		// "+0" label would misadvertise them; their effectName carries the real payoff.
		String eff = node.effect == SphereNode.Effect.KEYSTONE
				? effectName(node.effect)
				: "+" + node.magnitude + " " + effectName(node.effect);
		if (activated) {
			return "_" + node.name() + "_  (" + eff + ")  ✓";
		}
		if (!unlocked) {
			return node.name() + "  (" + eff + ")  — unlock: " + SphereGridProgress.unlockCost(node) + " Insight";
		}
		if (!grid.prerequisiteMet(node)) {
			return node.name() + "  (" + eff + ")  — needs " + node.requires + " active";
		}
		return node.name() + "  (" + eff + ")  — activate: " + node.cost + "pt";
	}

	private static String effectName(SphereNode.Effect e) {
		switch (e) {
			case EMBER: return "Ember (fire)";
			case FROST: return "Frost (chill)";
			case STORM: return "Storm (shock)";
			case MIGHT: return "Might (dmg)";
			case VIGOR: return "Vigor (hp)";
			case ABILITY: return "Ability (signature)";
			case KEYSTONE: return "Keystone: +1 ALL elements";
			default:    return e.name();
		}
	}
}
