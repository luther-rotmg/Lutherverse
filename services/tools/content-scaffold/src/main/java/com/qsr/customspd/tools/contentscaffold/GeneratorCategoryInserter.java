package com.qsr.customspd.tools.contentscaffold;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inserts an item class into a named Generator.Category's classes array, and appends a
 * matching weight to every prob literal of the same category so classes.length ==
 * probs.length still holds. Text-targeted.
 */
public final class GeneratorCategoryInserter {
    private GeneratorCategoryInserter() {}

    public static final String DEFAULT_WEIGHT = "1";

    /** No leading zero: "08" is not a valid Java int literal. */
    private static final Pattern WEIGHT = Pattern.compile("(0|[1-9]\\d*)(\\.\\d+)?");

    /**
     * Generator.random(Category) special-cases these and never reads their classes/probs, so an
     * item added to them would look wired and silently never drop.
     */
    private static final Set<String> DISPATCH_CATEGORIES = Set.of("WEAPON", "MISSILE", "ARMOR");

    /** "X.class" or "X.Y.class" elements of a classes literal. */
    private static final Pattern CLASS_ELEM = Pattern.compile("[A-Za-z0-9_.]+\\.class");
    /** Numeric elements of a float[] literal. */
    private static final Pattern PROB_ELEM = Pattern.compile("[^\\s,]+");
    /** The tail of an element's line when it is only an optional comma and a // comment. */
    private static final Pattern TRAILING_COMMENT = Pattern.compile("([ \\t]*)(,?)([ \\t]*//.*)");

    public static AnchorInserter.Result addItem(String generatorJava, String category, String className) {
        return addItem(generatorJava, category, className, DEFAULT_WEIGHT);
    }

    public static AnchorInserter.Result addItem(String generatorJava, String category, String className,
                                                String weight) {
        if (!WEIGHT.matcher(weight).matches()) {
            throw new IllegalArgumentException("Invalid weight '" + weight + "' (expected a non-negative number)");
        }
        // Match: Category.<CAT>.classes = new Class<?>[]{  ...  };  The "Category."
        // qualifier is optional -- the real Generator.java assigns these fields from
        // inside Category's own static block, where the enum constants (e.g. FOOD) are
        // in scope unqualified. Requiring "Category." unconditionally would never match
        // the real file, only test fixtures that happened to include it.
        String prefix = "(?<![A-Za-z0-9_$.])(?:Category\\.)?" + Pattern.quote(category);
        // Everything is located in the comment-masked text (same offsets), so a commented-out
        // literal is not live and a '}' inside a // comment does not end an array.
        String maskedFile = maskComments(generatorJava);
        Matcher m = Pattern.compile(prefix + "\\.classes\\s*=\\s*new\\s+Class<\\?>\\[\\]\\{")
                .matcher(maskedFile);
        if (!m.find()) {
            throw new IllegalArgumentException("No Category." + category + ".classes assignment found");
        }
        int open = m.end();                    // just after the '{'
        int close = maskedFile.indexOf('}', open);
        if (close < 0) throw new IllegalArgumentException("Unterminated classes array for " + category);
        // A bare body.contains(className + ".class") check would false-positive when
        // className is a suffix of an existing class name (e.g. adding "Berry" while
        // "SuperBerry.class" is already present) since there is no delimiter protecting
        // the identifier boundary. Require a non-identifier character (or start-of-body)
        // immediately before the class name. Comments are blanked first so a
        // "// see Berry.class" remark is neither an entry nor an insertion point.
        String masked = maskedFile.substring(open, close);
        Pattern present = Pattern.compile(
                "(?<![A-Za-z0-9_$])" + Pattern.quote(className) + "\\.class\\b");
        if (present.matcher(masked).find()) {
            return new AnchorInserter.Result(generatorJava, false);
        }
        if (DISPATCH_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Category." + category + " is a dispatch category: "
                    + "Generator.random() ignores its classes/probs, so the item would never drop. "
                    + "Add it to a tier category (WEP_T1..5, MIS_T1..5) instead");
        }

        // Every prob literal of this category must grow with classes. The runtime-derived
        // form (probs = defaultProbs.clone()) is not a literal and follows by itself.
        // Refuse -- rather than guess -- when none exists or one is already out of step.
        int classCount = count(masked, CLASS_ELEM);
        Pattern probLiteral = Pattern.compile(
                prefix + "\\.(defaultProbs2?|probs)\\s*=\\s*new\\s+float\\[\\]\\s*\\{");
        Matcher pm = probLiteral.matcher(maskedFile);
        List<int[]> edits = new ArrayList<>();           // {open, close, 0=classes|1=probs}
        edits.add(new int[]{open, close, 0});
        int probLiterals = 0;
        while (pm.find()) {
            int pOpen = pm.end();
            int pClose = maskedFile.indexOf('}', pOpen);
            if (pClose < 0) throw new IllegalArgumentException("Unterminated " + pm.group(1) + " array for " + category);
            int probCount = count(maskedFile.substring(pOpen, pClose), PROB_ELEM);
            if (probCount != classCount) {
                throw new IllegalArgumentException(category + ".classes has " + classCount + " entries but "
                        + category + "." + pm.group(1) + " has " + probCount
                        + "; fix the existing desync before scaffolding into it");
            }
            edits.add(new int[]{pOpen, pClose, 1});
            probLiterals++;
        }
        if (probLiterals == 0) {
            throw new IllegalArgumentException("No " + category + ".defaultProbs/probs array literal found; "
                    + "an item added to " + category + " would have no weight and never drop");
        }

        // Apply back to front so earlier offsets stay valid.
        edits.sort((a, b) -> Integer.compare(b[0], a[0]));
        StringBuilder out = new StringBuilder(generatorJava);
        for (int[] e : edits) {
            String old = generatorJava.substring(e[0], e[1]);
            String updated = e[2] == 0
                    ? appendElement(old, CLASS_ELEM, className + ".class", "\n    " + className + ".class\n  ")
                    : appendElement(old, PROB_ELEM, weight, " " + weight + " ");
            out.replace(e[0], e[1], updated);
        }

        // Post-condition: whatever the splice logic did, classes and every prob literal grew by
        // exactly one. A layout it mishandles fails here instead of shipping a silent desync.
        String maskedOut = maskComments(out.toString());
        Matcher cm = Pattern.compile(prefix + "\\.classes\\s*=\\s*new\\s+Class<\\?>\\[\\]\\{").matcher(maskedOut);
        if (!cm.find() || elementCount(maskedOut, cm.end(), CLASS_ELEM) != classCount + 1) {
            throw new IllegalArgumentException("Internal error: " + category
                    + ".classes did not grow by exactly one entry; Generator.java left untouched");
        }
        Matcher pm2 = probLiteral.matcher(maskedOut);
        int seen = 0;
        while (pm2.find()) {
            seen++;
            if (elementCount(maskedOut, pm2.end(), PROB_ELEM) != classCount + 1) {
                throw new IllegalArgumentException("Internal error: " + category + "." + pm2.group(1)
                        + " did not grow by exactly one entry; Generator.java left untouched");
            }
        }
        if (seen != probLiterals) {
            throw new IllegalArgumentException("Internal error: " + category + " prob literal count changed from "
                    + probLiterals + " to " + seen + "; Generator.java left untouched");
        }
        return new AnchorInserter.Result(out.toString(), true);
    }

    /** Elements matched by {@code elem} between {@code from} and the next '}' of {@code masked}. */
    private static int elementCount(String masked, int from, Pattern elem) {
        return count(masked.substring(from, masked.indexOf('}', from)), elem);
    }

    private static int count(String s, Pattern p) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /** Replaces each // comment with spaces of equal length so offsets are unchanged. */
    private static String maskComments(String s) {
        Matcher m = Pattern.compile("//[^\\n]*").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, " ".repeat(m.end() - m.start()));
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Appends {@code newElem} after the last element matched by {@code elem} (searched with
     * comments blanked), mimicking the array's layout. {@code emptyBody} replaces a body with no
     * elements.
     */
    private static String appendElement(String body, Pattern elem, String newElem, String emptyBody) {
        // Find the last element (regardless of formatting) and insert after it.
        // A regex anchored to end-of-body with a required leading '\n' would conflate a
        // single-line array with an empty one and overwrite existing entries; scanning for
        // every match and keeping the last position avoids that.
        Matcher elems = elem.matcher(maskComments(body));
        int lastStart = -1, lastEnd = -1;
        while (elems.find()) {
            lastStart = elems.start();
            lastEnd = elems.end();
        }
        if (lastEnd < 0) {
            // genuinely empty array
            return emptyBody;
        }
        int lineStart = body.lastIndexOf('\n', lastStart);
        if (lineStart >= 0) {
            // multi-line: mimic the last element's indentation
            // only the line's leading whitespace: the last element may share its line with others
            Matcher lead = Pattern.compile("^[ \\t]*").matcher(body.substring(lineStart + 1, lastStart));
            lead.find();
            String indent = lead.group();
            int eol = body.indexOf('\n', lastEnd);
            if (eol < 0) eol = body.length();
            Matcher comment = TRAILING_COMMENT.matcher(body.substring(lastEnd, eol));
            if (comment.matches()) {
                // The comma belongs right after the element, and the comment stays on its
                // own entry's line; the new element goes on the line below.
                return body.substring(0, lastEnd) + "," + comment.group(1) + comment.group(3) + "\n" + indent
                        + newElem + comment.group(2) + body.substring(eol);
            }
            return body.substring(0, lastEnd) + ",\n" + indent + newElem + body.substring(lastEnd);
        }
        // single-line: comma-separate, preserving existing entries
        return body.substring(0, lastEnd) + ", " + newElem + body.substring(lastEnd);
    }
}
