package com.qsr.customspd.tools.deletionaudit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every callable declared in one source file, at one git ref.
 *
 * <p>Unlike api-diff's {@code JavaSurface}, this includes private members and
 * records each body's statement count, so a deletion inside an unchanged
 * signature is visible.
 */
public record CallableInventory(List<Entry> entries) {

    /**
     * @param key            {@code TypeName#signature}, e.g. {@code CorpseDust#actions(Hero)}
     * @param visibility     {@code public}, {@code protected}, {@code private}, or {@code package}
     * @param statementCount total statements in the body, nested included; 0 when there is no body
     */
    public record Entry(String key, String visibility, int statementCount) {
    }

    public Map<String, Entry> byKey() {
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry entry : entries) {
            map.put(entry.key(), entry);
        }
        return map;
    }
}
