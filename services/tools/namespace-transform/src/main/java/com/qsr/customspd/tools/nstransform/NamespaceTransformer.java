package com.qsr.customspd.tools.nstransform;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bidirectional namespace transformer for SPD ↔ CPD package paths and Java/Kotlin imports.
 *
 * <p>Transforms both:
 * - Path segment: {@code com/shatteredpixel/shatteredpixeldungeon} ↔ {@code com/qsr/customspd}
 * - Java/Kotlin package/import text tokens
 *
 * Preserves all other bytes byte-for-byte.
 */
public final class NamespaceTransformer {

    private static final String SPD_PATH = "com/shatteredpixel/shatteredpixeldungeon";
    private static final String CPD_PATH = "com/qsr/customspd";
    private static final String SPD_NAMESPACE = "com.shatteredpixel.shatteredpixeldungeon";
    private static final String CPD_NAMESPACE = "com.qsr.customspd";

    public enum Direction {
        SPD_TO_CPD,
        CPD_TO_SPD
    }

    private final Direction direction;

    public NamespaceTransformer(Direction direction) {
        this.direction = direction;
    }

    /**
     * Transforms a source tree to a destination tree.
     *
     * @param inputDir source directory (must exist and be a directory)
     * @param outputDir destination directory (must not exist or must be empty;
     *                  must not be the same as inputDir)
     * @throws IOException on I/O error
     * @throws IllegalArgumentException if input and output resolve to the same
     *                                  directory or if destination collision occurs
     */
    public void transform(Path inputDir, Path outputDir) throws IOException {
        inputDir = inputDir.toRealPath();
        outputDir = outputDir.toAbsolutePath();

        if (!Files.isDirectory(inputDir)) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDir);
        }

        // Check if outputDir exists and get its real path for comparison
        Path outputDirReal = Files.exists(outputDir) ? outputDir.toRealPath() : outputDir;
        if (inputDir.equals(outputDirReal)) {
            throw new IllegalArgumentException("Input and output directories must be different");
        }

        // Collect all files deterministically (sorted by path)
        Map<String, Path> filesByRelativePath = new TreeMap<>();
        collectFiles(inputDir, inputDir, filesByRelativePath);

        // Track destination paths to detect collisions
        Map<Path, String> destinationToSource = new HashMap<>();

        // Create output directory and transform files
        Files.createDirectories(outputDir);

        for (Map.Entry<String, Path> entry : filesByRelativePath.entrySet()) {
            String relativePath = entry.getKey();
            Path sourcePath = entry.getValue();

            // Transform relative path
            String transformedRelativePath = transformPath(relativePath);
            Path destinationPath = outputDir.resolve(transformedRelativePath);

            // Check for collision
            if (destinationToSource.containsKey(destinationPath)) {
                throw new IllegalArgumentException(
                    "Destination collision: " + destinationToSource.get(destinationPath) +
                    " and " + relativePath + " both map to " + transformedRelativePath);
            }
            destinationToSource.put(destinationPath, relativePath);

            // Create destination directory if needed
            Path destinationParent = destinationPath.getParent();
            if (destinationParent != null) {
                Files.createDirectories(destinationParent);
            }

            // Copy or transform file
            if (isBinaryFile(sourcePath)) {
                Files.copy(sourcePath, destinationPath);
            } else {
                transformTextFile(sourcePath, destinationPath);
            }
        }
    }

    private void collectFiles(Path root, Path current, Map<String, Path> result) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    collectFiles(root, path, result);
                } else if (Files.isRegularFile(path)) {
                    String relativePath = root.relativize(path).toString();
                    // Normalize path separators to forward slashes
                    relativePath = relativePath.replace('\\', '/');
                    result.put(relativePath, path);
                }
            }
        }
    }

    private String transformPath(String relativePath) {
        String fromPath = (direction == Direction.SPD_TO_CPD) ? SPD_PATH : CPD_PATH;
        String toPath = (direction == Direction.SPD_TO_CPD) ? CPD_PATH : SPD_PATH;

        if (relativePath.startsWith(fromPath + "/")) {
            return toPath + "/" + relativePath.substring(fromPath.length() + 1);
        }
        return relativePath;
    }

    private void transformTextFile(Path source, Path destination) throws IOException {
        String name = source.getFileName().toString().toLowerCase();
        // Non-Java/Kotlin text files are copied byte-for-byte without content replacement
        if (!name.endsWith(".java") && !name.endsWith(".kt")) {
            Files.copy(source, destination);
            return;
        }
        String content = Files.readString(source);
        String transformed = transformContent(content);
        Files.writeString(destination, transformed);
    }

    private String transformContent(String content) {
        String fromNamespace = (direction == Direction.SPD_TO_CPD) ? SPD_NAMESPACE : CPD_NAMESPACE;
        String toNamespace = (direction == Direction.SPD_TO_CPD) ? CPD_NAMESPACE : SPD_NAMESPACE;

        // Replace exact full-qualified namespace tokens
        // Look for: package, import, extends, implements, throws, etc.
        return replaceNamespaceTokens(content, fromNamespace, toNamespace);
    }

    private String replaceNamespaceTokens(String content, String fromNamespace, String toNamespace) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < content.length()) {
            // --- Lexical state machine ---
            char c = content.charAt(i);

            // Line comment: skip until newline
            if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                int end = content.indexOf('\n', i);
                if (end == -1) {
                    end = content.length();
                } else {
                    end = end + 1; // include newline
                }
                result.append(content, i, end);
                i = end;
                continue;
            }

            // Block/Javadoc comment: skip until */
            if (c == '/' && i + 1 < content.length() && content.charAt(i + 1) == '*') {
                int end = content.indexOf("*/", i + 2);
                if (end == -1) {
                    end = content.length();
                } else {
                    end = end + 2; // include */
                }
                result.append(content, i, end);
                i = end;
                continue;
            }

            // Character literal: skip until closing quote (handle escapes)
            if (c == '\'') {
                result.append(c);
                i++;
                while (i < content.length()) {
                    char cc = content.charAt(i);
                    result.append(cc);
                    if (cc == '\\') {
                        // skip escaped char
                        i++;
                        if (i < content.length()) {
                            result.append(content.charAt(i));
                            i++;
                        }
                    } else if (cc == '\'') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                continue;
            }

            // String literal: skip until closing quote (handle escapes)
            if (c == '"') {
                // Check for Kotlin triple-quoted string """..."""
                if (i + 2 < content.length()
                        && content.charAt(i + 1) == '"'
                        && content.charAt(i + 2) == '"') {
                    // Kotlin triple-quoted string
                    result.append("\"\"\"");
                    i += 3;
                    while (i < content.length()) {
                        if (i + 2 < content.length()
                                && content.charAt(i) == '"'
                                && content.charAt(i + 1) == '"'
                                && content.charAt(i + 2) == '"') {
                            result.append("\"\"\"");
                            i += 3;
                            break;
                        }
                        result.append(content.charAt(i));
                        i++;
                    }
                } else {
                    // Normal string literal
                    result.append(c);
                    i++;
                    while (i < content.length()) {
                        char cc = content.charAt(i);
                        result.append(cc);
                        if (cc == '\\') {
                            // skip escaped char
                            i++;
                            if (i < content.length()) {
                                result.append(content.charAt(i));
                                i++;
                            }
                        } else if (cc == '"') {
                            i++;
                            break;
                        } else {
                            i++;
                        }
                    }
                }
                continue;
            }

            // --- Normal code context: check for namespace token ---
            if (startsWith(content, i, fromNamespace)) {
                // Verify this is a complete token:
                // 1. Preceded by non-namespace-part character (or start of string)
                // 2. Followed by: end of string, dot (for sub-packages), or non-identifier character
                //    BUT NOT by letters/digits/underscores (which would mean it's part of a larger identifier)
                boolean validBefore = (i == 0) || !isNamespacePartChar(content.charAt(i - 1));
                
                boolean validAfter;
                if (i + fromNamespace.length() >= content.length()) {
                    validAfter = true; // end of string
                } else {
                    char afterChar = content.charAt(i + fromNamespace.length());
                    // Valid if followed by: dot, space, semicolon, punctuation, etc.
                    // Invalid if followed by: letter, digit, underscore, or slash
                    validAfter = (afterChar == '.') || 
                        (!Character.isLetterOrDigit(afterChar) && afterChar != '_' && afterChar != '/');
                }

                if (validBefore && validAfter) {
                    result.append(toNamespace);
                    i += fromNamespace.length();
                } else {
                    result.append(content.charAt(i));
                    i++;
                }
            } else {
                result.append(content.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    private boolean isNamespacePartChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    private boolean startsWith(String str, int offset, String prefix) {
        if (offset + prefix.length() > str.length()) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (str.charAt(offset + i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBinaryFile(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        // Common binary extensions
        String[] binaryExtensions = {
            ".class", ".jar", ".zip", ".png", ".jpg", ".jpeg", ".gif", ".bmp",
            ".ico", ".ttf", ".otf", ".woff", ".woff2", ".mp3", ".ogg", ".wav",
            ".mp4", ".webm", ".apk", ".so", ".dll", ".exe", ".bin"
        };

        for (String ext : binaryExtensions) {
            if (name.endsWith(ext)) {
                return true;
            }
        }

        // Read file content once for binary detection
        byte[] content;
        try {
            content = Files.readAllBytes(path);
            if (content.length == 0) {
                return false; // Empty files are text
            }
            // If file contains null bytes, it's binary
            for (byte b : content) {
                if (b == 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Assume binary if we can't read
            return true;
        }
        return false;
    }
}
