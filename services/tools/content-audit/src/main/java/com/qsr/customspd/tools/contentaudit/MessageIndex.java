package com.qsr.customspd.tools.contentaudit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/** All localization keys present under {@code core/src/main/assets/messages/}. */
public final class MessageIndex {
    // actors_ru.properties, items_zh.properties, ... (language code, optional region)
    private static final java.util.regex.Pattern TRANSLATION =
            java.util.regex.Pattern.compile(".+_[a-z]{2,3}(_[A-Za-z]{2,4})?\\.properties");

    private final Set<String> keys;

    private MessageIndex(Set<String> keys) { this.keys = keys; }

    /** Empty index for the -Canary negative control. */
    public static MessageIndex empty() { return new MessageIndex(Set.of()); }

    /** In-memory index of exactly these keys (canary / tests). */
    public static MessageIndex of(String... keys) { return new MessageIndex(Set.of(keys)); }

    public static MessageIndex load(File repoRoot) throws IOException {
        Path msgs = repoRoot.toPath().resolve("core/src/main/assets/messages");
        Set<String> keys = new HashSet<>();
        if (Files.isDirectory(msgs)) {
            try (Stream<Path> walk = Files.walk(msgs)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (!p.toString().endsWith(".properties")) continue;
                    // Only the default (English) bundle counts: a translation (actors_ru.properties)
                    // must not mask a key missing from the file every player falls back to.
                    if (TRANSLATION.matcher(p.getFileName().toString()).matches()) continue;
                    for (String line : Files.readAllLines(p)) {
                        String t = line.strip();
                        if (t.isEmpty() || t.startsWith("#")) continue;
                        int eq = t.indexOf('=');
                        if (eq > 0) keys.add(t.substring(0, eq).strip());
                    }
                }
            }
        }
        return new MessageIndex(keys);
    }

    public boolean hasKey(String key) { return keys.contains(key); }
}
