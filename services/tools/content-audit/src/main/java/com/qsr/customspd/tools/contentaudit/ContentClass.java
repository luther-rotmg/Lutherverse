package com.qsr.customspd.tools.contentaudit;

/** A parsed content class. {@code spriteClass} and {@code imageAsset} are the
 *  simple names assigned in this class's own body (null if it does not assign one;
 *  EntityGraph resolves inherited values). */
public record ContentClass(
        String simpleName,
        String packageName,
        String superSimpleName,
        boolean isAbstract,
        String spriteClass,
        String imageAsset) {}
