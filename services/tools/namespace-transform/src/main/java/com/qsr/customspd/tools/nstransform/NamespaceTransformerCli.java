package com.qsr.customspd.tools.nstransform;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI entry point for the bidirectional namespace transformer.
 *
 * <p>Transforms namespace paths and Java/Kotlin package declarations between
 * SPD and CPD naming conventions. Supports both directions and validates
 * against in-place transformation and destination collisions.
 *
 * Usage: NamespaceTransformerCli --input <dir> --output <dir> --direction [spd-to-cpd|cpd-to-spd]
 */
public final class NamespaceTransformerCli {

    private NamespaceTransformerCli() {
    }

    public static void main(String[] args) {
        Args parsed;
        try {
            parsed = Args.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Usage: NamespaceTransformerCli --input <dir> --output <dir> --direction [spd-to-cpd|cpd-to-spd]");
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        Path inputDir = Paths.get(parsed.inputPath);
        Path outputDir = Paths.get(parsed.outputPath);

        NamespaceTransformer transformer = new NamespaceTransformer(parsed.direction);
        try {
            transformer.transform(inputDir, outputDir);
            System.out.println("Transform complete: " + parsed.direction + " from " + inputDir + " to " + outputDir);
            System.exit(0);
        } catch (IOException e) {
            System.err.println("Transform failed: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid arguments: " + e.getMessage());
            System.exit(1);
        }
    }

    private static final class Args {
        String inputPath;
        String outputPath;
        NamespaceTransformer.Direction direction;

        static Args parse(String[] args) throws IllegalArgumentException {
            Args result = new Args();
            boolean hasInput = false;
            boolean hasOutput = false;
            boolean hasDirection = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--input" -> {
                        result.inputPath = requireValue(args, ++i, "--input");
                        hasInput = true;
                    }
                    case "--output" -> {
                        result.outputPath = requireValue(args, ++i, "--output");
                        hasOutput = true;
                    }
                    case "--direction" -> {
                        String dirValue = requireValue(args, ++i, "--direction");
                        result.direction = parseDirection(dirValue);
                        hasDirection = true;
                    }
                    default -> throw new IllegalArgumentException("Unrecognized argument: " + args[i]);
                }
            }

            if (!hasInput) {
                throw new IllegalArgumentException("Missing required argument: --input");
            }
            if (!hasOutput) {
                throw new IllegalArgumentException("Missing required argument: --output");
            }
            if (!hasDirection) {
                throw new IllegalArgumentException("Missing required argument: --direction");
            }

            return result;
        }

        private static String requireValue(String[] args, int index, String flag) throws IllegalArgumentException {
            if (index >= args.length) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            return args[index];
        }

        private static NamespaceTransformer.Direction parseDirection(String value) throws IllegalArgumentException {
            return switch (value) {
                case "spd-to-cpd" -> NamespaceTransformer.Direction.SPD_TO_CPD;
                case "cpd-to-spd" -> NamespaceTransformer.Direction.CPD_TO_SPD;
                default -> throw new IllegalArgumentException("Unknown direction: " + value + " (expected spd-to-cpd or cpd-to-spd)");
            };
        }
    }
}
