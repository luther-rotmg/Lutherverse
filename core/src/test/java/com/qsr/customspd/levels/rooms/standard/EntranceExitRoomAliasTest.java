package com.qsr.customspd.levels.rooms.standard;

import com.qsr.customspd.ShatteredPixelDungeon;
import com.qsr.customspd.levels.rooms.Room;
import com.qsr.customspd.levels.rooms.standard.entrance.EntranceRoom;
import com.qsr.customspd.levels.rooms.standard.exit.ExitRoom;
import com.qsr.customspd.test.HeadlessGdx;
import com.watabou.utils.Bundle;
import com.watabou.utils.Bundlable;
import com.watabou.utils.Rect;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 1 moved EntranceRoom/ExitRoom into standard.entrance / standard.exit and registered
 * Bundle aliases so saves written under the old class names still load (RegularLevel bundles its
 * rooms). SPD-classes' BundleAliasRoundtripTest proves the mechanism generically but cannot see
 * core classes; this proves the two real registrations made by
 * {@link ShatteredPixelDungeon#registerBundleAliases()}.
 *
 * Bundle's alias map is global, add-only static state: nothing can un-register, and re-registering
 * is idempotent, so test order cannot change the outcome. The legacy-name tests therefore pair
 * with {@link #legacyNamesDoNotResolveWithoutTheAlias()}, which shows the old names resolve to
 * nothing by reflection alone, so a restored room can only have come from the alias.
 */
class EntranceExitRoomAliasTest {

	private static final String OLD_ENTRANCE = "com.qsr.customspd.levels.rooms.standard.EntranceRoom";
	private static final String OLD_EXIT = "com.qsr.customspd.levels.rooms.standard.ExitRoom";

	@BeforeAll
	static void boot() {
		HeadlessGdx.boot();
		ShatteredPixelDungeon.registerBundleAliases();
	}

	private static Bundle roundtrip(Bundle source) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		assertTrue(Bundle.write(source, out), "write must succeed");
		return Bundle.read(new ByteArrayInputStream(out.toByteArray()));
	}

	/** A bundle as an old save holds it: className set to the pre-move name, bounds as Room stores them. */
	private static Bundle legacyBundle(String oldClassName) throws IOException {
		Bundle room = new Bundle();
		room.put("__className", oldClassName);
		room.put("left", 3);
		room.put("top", 4);
		room.put("right", 11);
		room.put("bottom", 12);
		Bundle save = new Bundle();
		save.put("room", room);
		return roundtrip(save);
	}

	private static void assertBounds(Room room) {
		assertEquals(3, room.left);
		assertEquals(4, room.top);
		assertEquals(11, room.right);
		assertEquals(12, room.bottom);
	}

	@Test
	void legacyNamesDoNotResolveWithoutTheAlias() {
		assertNull(Reflection.forName(OLD_ENTRANCE), "old EntranceRoom name must no longer be a class");
		assertNull(Reflection.forName(OLD_EXIT), "old ExitRoom name must no longer be a class");
	}

	@Test
	void entranceRoomSurvivesAFreshRoundtrip() throws IOException {
		EntranceRoom room = new EntranceRoom();
		room.set(new Rect(3, 4, 11, 12));
		Bundle source = new Bundle();
		source.put("room", room);

		Bundlable restored = roundtrip(source).get("room");

		assertNotNull(restored, "a freshly saved EntranceRoom must come back");
		assertSame(EntranceRoom.class, restored.getClass());
		assertBounds((Room) restored);
	}

	@Test
	void exitRoomSurvivesAFreshRoundtrip() throws IOException {
		ExitRoom room = new ExitRoom();
		room.set(new Rect(3, 4, 11, 12));
		Bundle source = new Bundle();
		source.put("room", room);

		Bundlable restored = roundtrip(source).get("room");

		assertNotNull(restored, "a freshly saved ExitRoom must come back");
		assertSame(ExitRoom.class, restored.getClass());
		assertBounds((Room) restored);
	}

	@Test
	void entranceRoomSavedUnderTheOldClassNameRestoresAsTheCurrentClass() throws IOException {
		Bundlable restored = legacyBundle(OLD_ENTRANCE).get("room");

		assertNotNull(restored, "an old-save EntranceRoom must resolve through its alias; null means "
				+ "the Slice 1 registration is dead and old saves lose their entrance room");
		assertSame(EntranceRoom.class, restored.getClass());
		assertBounds((Room) restored);
	}

	@Test
	void exitRoomSavedUnderTheOldClassNameRestoresAsTheCurrentClass() throws IOException {
		Bundlable restored = legacyBundle(OLD_EXIT).get("room");

		assertNotNull(restored, "an old-save ExitRoom must resolve through its alias; null means "
				+ "the Slice 1 registration is dead and old saves lose their exit room");
		assertSame(ExitRoom.class, restored.getClass());
		assertBounds((Room) restored);
	}
}
