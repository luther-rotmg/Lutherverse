package com.qsr.customspd.test;

import com.badlogic.gdx.Preferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link Preferences} for {@code GameSettings.set(...)}. Headless libGDX preferences
 * live under the user's home directory, so reading them would make a test depend on whatever
 * settings the developer's machine has (an active challenge changes levelgen), and writing them
 * would leak test state onto that machine. flush() is a no-op: nothing ever touches disk.
 */
public final class MemoryPreferences implements Preferences {

	private final Map<String, Object> values = new LinkedHashMap<>();

	@Override public Preferences putBoolean(String key, boolean val) { values.put(key, val); return this; }
	@Override public Preferences putInteger(String key, int val) { values.put(key, val); return this; }
	@Override public Preferences putLong(String key, long val) { values.put(key, val); return this; }
	@Override public Preferences putFloat(String key, float val) { values.put(key, val); return this; }
	@Override public Preferences putString(String key, String val) { values.put(key, val); return this; }
	@Override public Preferences put(Map<String, ?> vals) { values.putAll(vals); return this; }

	@Override public boolean getBoolean(String key) { return getBoolean(key, false); }
	@Override public int getInteger(String key) { return getInteger(key, 0); }
	@Override public long getLong(String key) { return getLong(key, 0); }
	@Override public float getFloat(String key) { return getFloat(key, 0); }
	@Override public String getString(String key) { return getString(key, ""); }

	@Override public boolean getBoolean(String key, boolean defValue) {
		Object v = values.get(key);
		return v instanceof Boolean ? (Boolean) v : defValue;
	}
	@Override public int getInteger(String key, int defValue) {
		Object v = values.get(key);
		return v instanceof Number ? ((Number) v).intValue() : defValue;
	}
	@Override public long getLong(String key, long defValue) {
		Object v = values.get(key);
		return v instanceof Number ? ((Number) v).longValue() : defValue;
	}
	@Override public float getFloat(String key, float defValue) {
		Object v = values.get(key);
		return v instanceof Number ? ((Number) v).floatValue() : defValue;
	}
	@Override public String getString(String key, String defValue) {
		Object v = values.get(key);
		return v == null ? defValue : String.valueOf(v);
	}

	@Override public Map<String, ?> get() { return new LinkedHashMap<>(values); }
	@Override public boolean contains(String key) { return values.containsKey(key); }
	@Override public void clear() { values.clear(); }
	@Override public void remove(String key) { values.remove(key); }
	@Override public void flush() { }
}
