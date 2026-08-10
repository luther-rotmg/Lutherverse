/*
 * Custom Pixel Dungeon Ultimate
 *
 * Save-compat bridge scaffolding — Sub-B Slice 0.
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

package com.watabou.utils;

import com.watabou.utils.bridge.BundleTranslator;
import com.watabou.utils.bridge.PreV232Translator;
import com.watabou.utils.bridge.PreV242Translator;
import com.watabou.utils.bridge.PreV254Translator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Routes a legacy-version {@link Bundle} through the chain of
 * {@link BundleTranslator}s needed to bring it up to the current save format.
 *
 * Slice 0: scaffolding only. All translators in {@link #CHAIN} are no-op
 * passthrough stubs; real per-version translation logic lands in Slices
 * 3a / 5b / 6c as SPD's corresponding save-compat drops are ported.
 *
 * Slice 0: {@link #upcast(Bundle, String)} declares {@link BundleBridgeException}
 * and gates on a version whitelist before the translator chain fires;
 * unrecognized save formats are rejected rather than silently mishandled.
 */
public final class BundleBridge {

	//NB: java.util.List.of is Android API 34+. This app is minSdk 19 with no core library
	//desugaring configured, so List.of here would NoSuchMethodError on every device below
	//Android 14 the moment this class is loaded -- in the save-compat path, no less.
	//Kept Java 8 API only; enforced build-wide by options.release = 8 in the root build.gradle.
	private static final List<BundleTranslator> CHAIN = Collections.unmodifiableList(Arrays.asList(
		new PreV232Translator(),
		new PreV242Translator(),
		new PreV254Translator()
	));

	private BundleBridge() {}

	/**
	 * Upcast a Bundle from a legacy CPD/SPD version to the current format.
	 * The detected/declared version is checked against a whitelist before
	 * the chain fires; unrecognized formats throw {@link BundleBridgeException}
	 * rather than risk mishandling an unknown save layout.
	 * The chain fires each translator whose target version is greater than the sourceVersion.
	 * If sourceVersion is null or unrecognized, attempts signature-heuristic detection.
	 */
	public static Bundle upcast( Bundle input, String sourceVersion ) throws BundleBridgeException {
		String detected = (sourceVersion == null || sourceVersion.isEmpty())
				? detectVersion( input )
				: sourceVersion;
		if (!isKnownVersion( detected )) {
			throw new BundleBridgeException( "unsupported save format: " + detected );
		}
		Bundle current = input;
		for (BundleTranslator t : CHAIN) {
			if (versionLessThan( detected, t.targetVersion() )) {
				current = t.upcast( current );
				detected = t.targetVersion();
			}
		}
		return current;
	}

	/**
	 * Version-whitelist gate: only formats the bridge knows how to route
	 * (or the {@code "unknown"} sentinel from {@link #detectVersion(Bundle)})
	 * are allowed through to the translator chain.
	 */
	private static boolean isKnownVersion( String version ) {
		return version.startsWith( "cpd-" )
				|| version.startsWith( "v2." )
				|| version.startsWith( "v3." )
				|| version.equals( "unknown" );
	}

	private static String detectVersion( Bundle b ) {
		// Slice 0 heuristic: check for known bundle keys.
		// Populated more in future slices as translators need to sniff different versions.
		if (b.contains( "version" )) return b.getString( "version" );
		return "unknown";
	}

	private static boolean versionLessThan( String a, String target ) {
		// Simple lex compare for v-prefixed strings; refine in Slice 3a
		// when we need real semver comparison including CPD-suffix variants.
		return a.compareTo( target ) < 0;
	}
}
