package com.watabou.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 1 moved EntranceRoom/ExitRoom between packages and depended on
 * {@code Bundle.addAlias} to keep old saves loading. Nothing had ever verified
 * that addAlias resolves.
 *
 * <p>These tests pin the mechanism using a test-local class. They deliberately
 * do NOT cover the actual registrations: core depends on SPD-classes and never
 * the reverse, so no test here can reference EntranceRoom, ExitRoom, or core's
 * terrain constants. Auditing those needs a static checker or a core test
 * source set, tracked separately.
 */
class BundleAliasRoundtripTest {

	/** Stands in for a game class that was moved between packages. */
	public static class MovedThing implements Bundlable {
		int value;

		@Override
		public void storeInBundle(Bundle bundle) {
			bundle.put("value", value);
		}

		@Override
		public void restoreFromBundle(Bundle bundle) {
			value = bundle.getInt("value");
		}
	}

	private static Bundle roundtrip(Bundle source) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertTrue(Bundle.write(source, out), "write must succeed");
		return Bundle.read(new ByteArrayInputStream(out.toByteArray()));
	}

	@Test
	void aBundlableSurvivesAWriteReadRoundtrip() throws IOException {
		MovedThing thing = new MovedThing();
		thing.value = 42;

		Bundle source = new Bundle();
		source.put("thing", thing);

		Bundle restored = roundtrip(source);
		Bundlable result = restored.get("thing");

		assertNotNull(result, "the bundled object must come back");
		assertEquals(42, ((MovedThing) result).value);
	}

	@Test
	void anAliasedLegacyClassNameResolvesToTheCurrentClass() throws IOException {
		// Simulate a save written before the class moved: the persisted
		// __className is the OLD fully-qualified name, which no longer exists.
		String legacyName = "com.watabou.utils.legacy.OldMovedThing";
		Bundle.addAlias(MovedThing.class, legacyName);

		String json = "{\"thing\":{\"__className\":\"" + legacyName + "\",\"value\":7}}";
		Bundle restored = Bundle.read(
				new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

		Bundlable result = restored.get("thing");

		assertNotNull(result,
				"an aliased legacy class name must resolve; if this is null, every "
						+ "addAlias registration Slice 1 added is silently dead and old "
						+ "saves lose these objects");
		assertEquals(7, ((MovedThing) result).value);
	}

	@Test
	void anUnaliasedMissingClassDoesNotResolve() throws IOException {
		// The negative control: without a registration, a vanished class must not
		// come back. Without this, the alias test above could pass for the wrong
		// reason (e.g. some fallback resolution path).
		String json = "{\"thing\":{\"__className\":"
				+ "\"com.watabou.utils.legacy.NeverRegistered\",\"value\":9}}";
		Bundle restored = Bundle.read(
				new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

		assertNull(restored.get("thing"),
				"an unregistered vanished class must not resolve");
	}
}
