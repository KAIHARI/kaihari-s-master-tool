/*
 * Kotlin's `kotlin.random.Random`, reproduced exactly.
 *
 * ## Why this file exists at all
 *
 * `PlayField.riffled` writes Fisher-Yates out by hand rather than calling the
 * standard library's shuffle, and says why: the stdlib is free to change its
 * algorithm between versions and platforms, and a seed that dealt one hand on a
 * tablet and another on a desktop would make every bug report about an opening
 * hand unreproducible.
 *
 * That argument does not survive being ported unless the *generator* comes over
 * too. Writing Fisher-Yates identically in C and then feeding it `rand()` gives
 * a shuffle that is correct, deterministic, and a different deal - which is the
 * exact failure the Kotlin took the trouble to avoid, arriving through the one
 * door it left open.
 *
 * So this is XorWow as Kotlin implements it: the same six words of state, the
 * same seeding from a Long, the same 64 discarded outputs, the same rejection
 * loop in `nextInt(bound)`. `mt_random.txt` in the vectors holds thousands of
 * its outputs beside Kotlin's.
 *
 * All arithmetic is unsigned. Kotlin's Int wraps on overflow; C's signed
 * overflow is undefined, so every step is done in uint32_t and cast at the
 * boundary. That is not pedantry - at -O2 a compiler may assume signed overflow
 * cannot happen and delete the wrap this generator is built on.
 */
#ifndef MT_RANDOM_H
#define MT_RANDOM_H

#include <stdint.h>

typedef struct {
    uint32_t x, y, z, w, v, addend;
} MtRandom;

/** Seeds exactly as Kotlin's `Random(seed: Long)` does. */
void mt_random_seed(MtRandom *r, int64_t seed);

/** Kotlin's `nextInt()`: a full 32-bit value, signed. */
int32_t mt_random_next_int(MtRandom *r);

/** Kotlin's `nextBits(bitCount)`. */
int32_t mt_random_next_bits(MtRandom *r, int bit_count);

/**
 * Kotlin's `nextInt(until)` - uniform in [0, until).
 *
 * The rejection loop matters: for a bound that is not a power of two Kotlin
 * takes the top 31 bits, reduces modulo the bound, and retries when the draw
 * fell in the short tail. Taking a plain modulo instead agrees with it most of
 * the time, which is the worst possible way to disagree.
 */
int32_t mt_random_next_int_bound(MtRandom *r, int32_t until);

#endif /* MT_RANDOM_H */
