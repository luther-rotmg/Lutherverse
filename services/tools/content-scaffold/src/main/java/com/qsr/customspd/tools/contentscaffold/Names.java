package com.qsr.customspd.tools.contentscaffold;

/** Derives the naming variants a content entity needs from its PascalCase class name. */
public record Names(String className, String snake, String upperSnake) {

    public static Names of(String pascal) {
        if (pascal == null || pascal.isBlank() || !Character.isUpperCase(pascal.charAt(0))) {
            throw new IllegalArgumentException("Name must be a non-empty PascalCase identifier: " + pascal);
        }
        StringBuilder snakeB = new StringBuilder();
        for (int i = 0; i < pascal.length(); i++) {
            char c = pascal.charAt(i);
            if (Character.isUpperCase(c) && i > 0) snakeB.append('_');
            snakeB.append(Character.toLowerCase(c));
        }
        String snake = snakeB.toString();
        return new Names(pascal, snake, snake.toUpperCase());
    }

    /** Message-key leaf, matching content-audit's derivation: the whole class name lowercased. */
    public String lower() { return className.toLowerCase(); }

    public String mobAssetPath() { return "sprites/chars/" + snake + ".png"; }

    public String itemAssetPath() { return "sprites/items/" + snake + ".png"; }
}
