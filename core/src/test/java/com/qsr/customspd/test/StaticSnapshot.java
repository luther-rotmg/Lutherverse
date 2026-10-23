package com.qsr.customspd.test;

import com.badlogic.gdx.utils.IntMap;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflective snapshot of a list of classes' global state, restorable in one call. For each class
 * it captures every static field; for an enum every constant's instance fields
 * (LimitedDrops.count, Generator.Category.probs/seed/dropped, Catalog.seen...); and, one level
 * deep, the fields of any plain game object a static holds (Dungeon.quickslot, Kotlin objects).
 * Restoring puts each non-final field back to the exact object it held AND puts that object's
 * contents back: collections, maps, libGDX IntMaps (SparseArray) and arrays are copied shallowly
 * at capture, so state mutated in place (Actor.all, Catalog's seen maps, a deck array) is undone.
 *
 * Shallow by design: element objects themselves are not deep-copied. That is sufficient for the
 * game's run-start statics because Dungeon.init / loadGame replace their objects (a new Hero,
 * new item handlers, a new Random stack) rather than mutate the old ones; the
 * {@code HeadlessRunHygieneTest} fingerprint proves it for the whole codebase.
 */
public final class StaticSnapshot {

	private static final class Slot {
		final Field field;
		final Object owner; // null for a static field, the owning object otherwise
		final Object value;
		final Object contents;

		Slot(Field field, Object owner) throws IllegalAccessException {
			this.field = field;
			this.owner = owner;
			this.value = field.get(owner);
			this.contents = copyContents(value);
		}

		void restore() throws IllegalAccessException {
			if (!Modifier.isFinal(field.getModifiers())) {
				field.set(owner, value);
			}
			restoreContents(value, contents);
		}
	}

	private final List<Slot> slots = new ArrayList<>();

	private StaticSnapshot() {}

	public static StaticSnapshot capture(List<Class<?>> classes) {
		StaticSnapshot snap = new StaticSnapshot();
		for (Class<?> c : classes) {
			List<Slot> slots = new ArrayList<>();
			try {
				for (Field f : c.getDeclaredFields()) {
					if (f.isSynthetic() || !Modifier.isStatic(f.getModifiers()) || skip(f)) continue;
					f.setAccessible(true);
					Slot s = new Slot(f, null);
					slots.add(s);
					if (isGameObject(s.value)) addMembers(slots, s.value);
				}
				if (c.isEnum()) {
					for (Object constant : c.getEnumConstants()) {
						addMembers(slots, constant);
					}
				}
			} catch (LinkageError e) {
				// static init needs a GL context or similar: the class never initialises
				// headless, so it holds no state a run could change
				continue;
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("could not snapshot " + c.getName(), e);
			}
			snap.slots.addAll(slots);
		}
		return snap;
	}

	/** Puts every captured field and container back exactly as captured. Idempotent. */
	public void restore() {
		try {
			for (Slot s : slots) {
				s.restore();
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("could not restore global state", e);
		}
	}

	/**
	 * A plain game/engine object held by a static (a Kotlin object's INSTANCE, Dungeon.quickslot):
	 * its own fields are state too. Enums are covered via their class, containers via contents.
	 */
	static boolean isGameObject(Object v) {
		if (v == null || v instanceof Enum || v instanceof Class || v instanceof Collection
				|| v instanceof Map || v instanceof IntMap || v.getClass().isArray()) {
			return false;
		}
		String n = v.getClass().getName();
		return n.startsWith("com.qsr.customspd.") || n.startsWith("com.watabou.");
	}

	/** One level deep: the instance fields of an enum constant or static-held object, up its hierarchy. */
	private static void addMembers(List<Slot> slots, Object owner) throws IllegalAccessException {
		for (Class<?> k = owner.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			for (Field f : k.getDeclaredFields()) {
				if (f.isSynthetic() || Modifier.isStatic(f.getModifiers()) || skip(f)) continue;
				f.setAccessible(true);
				slots.add(new Slot(f, owner));
			}
		}
	}

	/** Final fields that cannot hold mutable state (constants, enum self-references) are skipped. */
	private static boolean skip(Field f) {
		if (!Modifier.isFinal(f.getModifiers())) return false;
		Class<?> t = f.getType();
		return t.isPrimitive() || t == String.class || t.isEnum()
				|| Number.class.isAssignableFrom(t) || t == Boolean.class || t == Character.class;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object copyContents(Object v) {
		if (v instanceof Collection) return new ArrayList<>((Collection) v);
		if (v instanceof Map) return new LinkedHashMap<>((Map) v);
		if (v instanceof IntMap) return new IntMap<>((IntMap) v);
		if (v != null && v.getClass().isArray()) {
			int n = Array.getLength(v);
			Object copy = Array.newInstance(v.getClass().getComponentType(), n);
			System.arraycopy(v, 0, copy, 0, n);
			return copy;
		}
		return null;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void restoreContents(Object v, Object contents) {
		// Untouched containers are left alone: some are immutable (Arrays.asList constants).
		if (contents == null || sameContents(v, contents)) return;
		if (v instanceof Collection) {
			((Collection) v).clear();
			((Collection) v).addAll((Collection) contents);
		} else if (v instanceof Map) {
			((Map) v).clear();
			((Map) v).putAll((Map) contents);
		} else if (v instanceof IntMap) {
			((IntMap) v).clear();
			((IntMap) v).putAll((IntMap) contents);
		} else {
			System.arraycopy(contents, 0, v, 0, Array.getLength(contents));
		}
	}

	/** Identity-wise, in iteration order. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static boolean sameContents(Object v, Object contents) {
		if (v instanceof Collection) {
			return sameElements(new ArrayList<Object>((Collection) v), (List<Object>) contents);
		} else if (v instanceof Map) {
			Map<Object, Object> now = (Map<Object, Object>) v;
			Map<Object, Object> then = (Map<Object, Object>) contents;
			if (now.size() != then.size()) return false;
			List<Object> nowFlat = new ArrayList<>();
			List<Object> thenFlat = new ArrayList<>();
			for (Map.Entry<Object, Object> e : now.entrySet()) { nowFlat.add(e.getKey()); nowFlat.add(e.getValue()); }
			for (Map.Entry<Object, Object> e : then.entrySet()) { thenFlat.add(e.getKey()); thenFlat.add(e.getValue()); }
			return sameElements(nowFlat, thenFlat);
		} else if (v instanceof IntMap) {
			IntMap now = (IntMap) v;
			IntMap then = (IntMap) contents;
			if (now.size != then.size) return false;
			for (Object o : then.entries()) {
				IntMap.Entry e = (IntMap.Entry) o;
				if (!now.containsKey(e.key) || now.get(e.key) != e.value) return false;
			}
			return true;
		}
		return false; // arrays are always mutable: copying them back is cheap and harmless
	}

	private static boolean sameElements(List<Object> a, List<Object> b) {
		if (a.size() != b.size()) return false;
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i) != b.get(i)) return false;
		}
		return true;
	}
}
