package com.qsr.customspd.test;

import com.qsr.customspd.Dungeon;
import com.qsr.customspd.modding.JsonConfigRetriever;
import com.watabou.utils.Random;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Every game and engine class on the test classpath: core's Java and Kotlin output and
 * SPD-classes (directories or jars, whichever Gradle supplies), restricted to the
 * {@code com.qsr.customspd} and {@code com.watabou} packages. Loaded without initialising;
 * scanned once per JVM, in a sorted (deterministic) order.
 */
public final class GameClasses {

	private static List<Class<?>> all;

	public static synchronized List<Class<?>> all() {
		if (all != null) return all;
		try {
			Set<File> roots = new LinkedHashSet<>();
			for (Class<?> anchor : new Class<?>[]{Dungeon.class, JsonConfigRetriever.class, Random.class}) {
				roots.add(new File(anchor.getProtectionDomain().getCodeSource().getLocation().toURI()));
			}
			List<String> names = new ArrayList<>();
			for (File root : roots) {
				if (root.isDirectory()) {
					collect(root, root, names);
				} else {
					try (JarFile jar = new JarFile(root)) {
						Enumeration<JarEntry> entries = jar.entries();
						while (entries.hasMoreElements()) {
							String n = entries.nextElement().getName();
							if (n.endsWith(".class")) names.add(n.substring(0, n.length() - 6).replace('/', '.'));
						}
					}
				}
			}
			Collections.sort(names);
			List<Class<?>> classes = new ArrayList<>();
			for (String name : names) {
				if (!name.startsWith("com.qsr.customspd.") && !name.startsWith("com.watabou.")) continue;
				try {
					classes.add(Class.forName(name, false, GameClasses.class.getClassLoader()));
				} catch (Throwable t) {
					// a class that cannot even load cannot hold state either
				}
			}
			if (classes.size() < 1000) {
				throw new IllegalStateException("classpath scan found only " + classes.size() + " game classes in " + roots);
			}
			all = Collections.unmodifiableList(classes);
			return all;
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("could not scan the game classes", e);
		}
	}

	private static void collect(File root, File dir, List<String> names) {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File f : files) {
			if (f.isDirectory()) {
				collect(root, f, names);
			} else if (f.getName().endsWith(".class")) {
				String rel = root.toURI().relativize(f.toURI()).getPath();
				names.add(rel.substring(0, rel.length() - 6).replace('/', '.'));
			}
		}
	}

	private GameClasses() {}
}
