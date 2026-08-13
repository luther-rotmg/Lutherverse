package com.qsr.customspd.tools.contentscaffold;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Inserts an item class into a named Generator.Category's classes array. Text-targeted. */
public final class GeneratorCategoryInserter {
    private GeneratorCategoryInserter() {}

    public static AnchorInserter.Result addItem(String generatorJava, String category, String className) {
        // Match: Category.<CAT>.classes = new Class<?>[]{  ...  };
        Matcher m = Pattern.compile(
                "Category\\." + Pattern.quote(category) + "\\.classes\\s*=\\s*new\\s+Class<\\?>\\[\\]\\{")
                .matcher(generatorJava);
        if (!m.find()) {
            throw new IllegalArgumentException("No Category." + category + ".classes assignment found");
        }
        int open = m.end();                    // just after the '{'
        int close = generatorJava.indexOf('}', open);
        if (close < 0) throw new IllegalArgumentException("Unterminated classes array for " + category);
        String body = generatorJava.substring(open, close);
        // A bare body.contains(className + ".class") check would false-positive when
        // className is a suffix of an existing class name (e.g. adding "Berry" while
        // "SuperBerry.class" is already present) since there is no delimiter protecting
        // the identifier boundary. Require a non-identifier character (or start-of-body)
        // immediately before the class name.
        Pattern present = Pattern.compile(
                "(?<![A-Za-z0-9_$])" + Pattern.quote(className) + "\\.class\\b");
        if (present.matcher(body).find()) {
            return new AnchorInserter.Result(generatorJava, false);
        }
        // Find the last "X.class" element (regardless of formatting) and insert after it.
        // A regex anchored to end-of-body with a required leading '\n' would conflate a
        // single-line array with an empty one and overwrite existing entries; scanning for
        // every match and keeping the last position avoids that.
        Matcher elems = Pattern.compile("[A-Za-z0-9_.]+\\.class").matcher(body);
        int lastStart = -1, lastEnd = -1;
        while (elems.find()) {
            lastStart = elems.start();
            lastEnd = elems.end();
        }
        String newBody;
        if (lastEnd < 0) {
            // genuinely empty array
            newBody = "\n    " + className + ".class\n  ";
        } else {
            int lineStart = body.lastIndexOf('\n', lastStart);
            if (lineStart >= 0) {
                // multi-line: mimic the last element's indentation
                String indent = body.substring(lineStart + 1, lastStart);
                newBody = body.substring(0, lastEnd) + ",\n" + indent + className + ".class"
                        + body.substring(lastEnd);
            } else {
                // single-line: comma-separate, preserving existing entries
                newBody = body.substring(0, lastEnd) + ", " + className + ".class" + body.substring(lastEnd);
            }
        }
        return new AnchorInserter.Result(
                generatorJava.substring(0, open) + newBody + generatorJava.substring(close), true);
    }
}
