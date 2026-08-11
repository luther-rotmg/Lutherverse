@file:JvmName("RandomGenUtils")
package com.qsr.customspd.modding

import com.watabou.utils.Random

// These pick the floors that guaranteed drops and quest NPCs land on, so they MUST draw from
// the game's seeded generator.
//
// They previously used Kotlin's stdlib: `shuffled()` is backed by an unseeded
// java.util.Random and `random()` by kotlin.random.Random.Default. Neither consults
// com.watabou.utils.Random, so the same seed produced different scroll/potion floors and
// different quest-NPC floors on every run -- defeating labelled seed sharing, daily runs,
// deterministic coop and any replay verifier.
//
// Random.shuffle and Random.element both route through Random.Int(max), which defaults to
// useGeneratorStack=true and therefore honours Dungeon.init's Random.pushGenerator(seed+1).
// That only helps if these are CALLED inside the pushed-generator block -- see Dungeon.init.

fun calculateLevels(distributions: List<ItemDistribution>): Array<String> =
    distributions.flatMap { distribution ->
        val pool = distribution.levels.toTypedArray()
        Random.shuffle(pool)
        pool.take(distribution.quantity)
    }.toTypedArray()

fun calculateQuestLevel(distribution: List<String>): String? =
    if (distribution.isNotEmpty()) Random.element(distribution) else null

fun halveQuantities(distributions: List<ItemDistribution>) =
    distributions.map { it.copy(quantity = it.quantity / 2) }
