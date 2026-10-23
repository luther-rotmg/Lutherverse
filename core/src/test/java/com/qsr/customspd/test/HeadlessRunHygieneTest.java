package com.qsr.customspd.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves {@link HeadlessRun} leaves NO global state behind, for the whole codebase rather than a
 * hand-picked list: it fingerprints every static field, every enum constant's fields and the
 * fields of every static-held game object, for every game/engine class on the test classpath
 * ({@link GameClasses}); runs a full real run (init, levelgen, descend, saves,
 * continue-from-save, direct generation); closes it; and requires an identical fingerprint.
 * Any global the run changes and {@link StaticSnapshot} fails to restore is reported by name.
 *
 * The mid-run fingerprint must DIFFER (Dungeon.hero, the Random stack...), so the comparison is shown
 * to see the changes it is meant to catch.
 */
class HeadlessRunHygieneTest {

	@BeforeAll
	static void initialiseEverything() {
		HeadlessGdx.boot();
		fingerprint(); // initialise every class once, so lazy static init is not reported as a change
	}

	@Test
	void aFullRunLeavesEveryStaticExactlyAsItFoundIt() {
		Map<String, String> before = fingerprint();
		Map<String, String> during;

		try (HeadlessRun run = HeadlessRun.start(424242L)) {
			run.enterFirstLevel();
			run.descend();
			run.saveAll();
			run.restore();
			run.generate("7");
			during = fingerprint();
		}
		Map<String, String> after = fingerprint();

		List<String> touched = diff(before, during);
		assertTrue(touched.contains("com.qsr.customspd.Dungeon.hero")
						&& touched.contains("com.watabou.utils.Random.generators")
						&& touched.contains("com.qsr.customspd.Dungeon.quickslot->QuickSlot.slots"),
				"the fingerprint must see a live run's state, or the comparison below is vacuous: " + touched);

		List<String> leaked = diff(before, after);
		if (!leaked.isEmpty()) {
			StringBuilder sb = new StringBuilder("HeadlessRun leaked global state (StaticSnapshot did not restore it):");
			for (String k : leaked) {
				sb.append("\n  ").append(k).append(" : ").append(clip(before.get(k))).append(" -> ").append(clip(after.get(k)));
			}
			fail(sb.toString());
		}
	}

	// ------------------------------------------------------------------------------ fingerprint

	private static Map<String, String> fingerprint() {
		Map<String, String> out = new TreeMap<>();
		for (Class<?> c : GameClasses.all()) {
			try {
				for (Field f : c.getDeclaredFields()) {
					if (f.isSynthetic() || !Modifier.isStatic(f.getModifiers())) continue;
					f.setAccessible(true);
					Object v = f.get(null);
					String key = c.getName() + "." + f.getName();
					out.put(key, describe(v));
					if (StaticSnapshot.isGameObject(v)) members(out, key + "->", v);
				}
				if (c.isEnum()) {
					for (Object constant : c.getEnumConstants()) {
						members(out, c.getName() + "#" + constant + ".", constant);
					}
				}
			} catch (Throwable t) {
				// classes whose static init needs a GL context never initialise headless: no state
			}
		}
		return out;
	}

	private static void members(Map<String, String> out, String prefix, Object owner) throws IllegalAccessException {
		for (Class<?> k = owner.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			for (Field f : k.getDeclaredFields()) {
				if (f.isSynthetic() || Modifier.isStatic(f.getModifiers())) continue;
				f.setAccessible(true);
				out.put(prefix + k.getSimpleName() + "." + f.getName(), describe(f.get(owner)));
			}
		}
	}

	/** Identity for objects; identity plus element identities for containers; values for arrays. */
	private static String describe(Object v) {
		if (v == null) return "null";
		if (v instanceof Number || v instanceof Boolean || v instanceof Character
				|| v instanceof String || v instanceof Enum || v instanceof Class) {
			return String.valueOf(v);
		}
		StringBuilder sb = new StringBuilder();
		sb.append(v.getClass().getName()).append('@').append(System.identityHashCode(v));
		if (v instanceof Collection) {
			sb.append('[');
			for (Object o : (Collection<?>) v) sb.append(describeElement(o)).append(',');
			sb.append(']');
		} else if (v instanceof Map) {
			sb.append('{');
			for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
				sb.append(describeElement(e.getKey())).append('=').append(describeElement(e.getValue())).append(',');
			}
			sb.append('}');
		} else if (v instanceof com.badlogic.gdx.utils.IntMap) {
			sb.append('{');
			for (Object o : ((com.badlogic.gdx.utils.IntMap<?>) v).entries()) {
				com.badlogic.gdx.utils.IntMap.Entry<?> e = (com.badlogic.gdx.utils.IntMap.Entry<?>) o;
				sb.append(e.key).append('=').append(describeElement(e.value)).append(',');
			}
			sb.append('}');
		} else if (v instanceof int[]) {
			sb.append(Arrays.toString((int[]) v));
		} else if (v instanceof float[]) {
			sb.append(Arrays.toString((float[]) v));
		} else if (v instanceof boolean[]) {
			sb.append(Arrays.toString((boolean[]) v));
		} else if (v instanceof long[]) {
			sb.append(Arrays.toString((long[]) v));
		} else if (v instanceof Object[]) {
			sb.append('[');
			for (Object o : (Object[]) v) sb.append(describeElement(o)).append(',');
			sb.append(']');
		}
		return sb.toString();
	}

	private static String describeElement(Object o) {
		if (o == null || o instanceof Number || o instanceof Boolean || o instanceof String
				|| o instanceof Enum || o instanceof Class) {
			return String.valueOf(o);
		}
		return "@" + System.identityHashCode(o);
	}

	private static List<String> diff(Map<String, String> a, Map<String, String> b) {
		List<String> changed = new ArrayList<>();
		for (String k : a.keySet()) {
			if (!Objects.equals(a.get(k), b.get(k))) changed.add(k);
		}
		return changed;
	}

	private static String clip(String s) {
		return s == null || s.length() <= 160 ? s : s.substring(0, 160) + "...";
	}
}
