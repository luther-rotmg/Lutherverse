package com.qsr.customspd.test;

import com.watabou.utils.Bundlable;
import com.watabou.utils.Bundle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * F2 save-roundtrip harness. Serializes game state through the REAL save path
 * ({@code Bundle.write} -> bytes -> {@code Bundle.read}) and hands back the reconstructed
 * value, so tests can assert that serialized state survives an actual save/load. This is the
 * "no save-roundtrip harness exists" gap the project flagged for the terrain-ID and
 * Bundle.addAlias work; it is deliberately tiny and pure (no libGDX), though objects that touch
 * asset/Gdx state during (de)serialization still need {@link HeadlessGdx#boot()} first.
 */
public final class SaveRoundtrip {

	/**
	 * Round-trip a Bundlable stored under a key in a parent bundle. The returned instance is
	 * reconstructed by Bundle via Reflection (exercises {@code __className} resolution too).
	 */
	public static <T extends Bundlable> T of(T in) throws IOException {
		Bundle parent = new Bundle();
		parent.put("obj", in);
		@SuppressWarnings("unchecked")
		T out = (T) writeRead(parent).get("obj");
		return out;
	}

	/**
	 * Round-trip a whole bundle — for objects that serialize by writing into a bundle directly
	 * (e.g. {@code hero.storeInBundle(b)} then a fresh {@code hero.restoreFromBundle(read)}).
	 */
	public static Bundle writeRead(Bundle in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!Bundle.write(in, out)) {
			throw new IOException("Bundle.write failed");
		}
		return Bundle.read(new ByteArrayInputStream(out.toByteArray()));
	}

	private SaveRoundtrip() {}
}
