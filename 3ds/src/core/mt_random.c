#include "mt_random.h"

int32_t mt_random_next_int(MtRandom *r) {
    /* Marsaglia, G. 2003. Xorshift RNGs. J. Statis. Soft. 8, 14, p. 5 */
    uint32_t t = r->x;
    t ^= t >> 2;
    r->x = r->y;
    r->y = r->z;
    r->z = r->w;
    uint32_t v0 = r->v;
    r->w = v0;
    t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4);
    r->v = t;
    r->addend += 362437u;
    return (int32_t)(t + r->addend);
}

void mt_random_seed(MtRandom *r, int64_t seed) {
    /* Kotlin: Random(seed) = XorWowRandom(seed.toInt(), (seed shr 32).toInt()) */
    uint32_t seed1 = (uint32_t)(seed & 0xFFFFFFFFu);
    uint32_t seed2 = (uint32_t)((uint64_t)seed >> 32);

    r->x = seed1;
    r->y = seed2;
    r->z = 0;
    r->w = 0;
    r->v = ~seed1;                      /* seed1.inv() */
    r->addend = (seed1 << 10) ^ (seed2 >> 4);

    /*
     * Kotlin discards 64 outputs here, and the comment on its own line says
     * why: some trivial seeds otherwise produce several values with zeroes in
     * the upper bits. Skipping this makes the first few draws of a low-numbered
     * seed differ, which is precisely the range a deal seed lives in.
     */
    for (int i = 0; i < 64; ++i) mt_random_next_int(r);
}

/*
 * Kotlin's `Int.takeUpperBits`, which is `ushr(32 - bitCount) and (-bitCount shr 31)`.
 *
 * The two endpoints are why this is not written as that expression. At
 * bitCount 0 the JVM masks a shift count to five bits, so `ushr(32)` is
 * `ushr(0)` and returns the value unchanged - and the mask term, which is zero
 * exactly there, is what turns it into 0. In C a shift of 32 on a uint32_t is
 * undefined rather than masked, so the same expression is a trapdoor. Both ends
 * are handled explicitly instead.
 */
static int32_t take_upper_bits(int32_t value, int bit_count) {
    if (bit_count <= 0) return 0;
    if (bit_count >= 32) return value;
    return (int32_t)(((uint32_t)value) >> (32 - bit_count));
}

int32_t mt_random_next_bits(MtRandom *r, int bit_count) {
    return take_upper_bits(mt_random_next_int(r), bit_count);
}

/** Kotlin's `fastLog2`: 31 - numberOfLeadingZeros(n). */
static int fast_log2(int32_t n) {
    uint32_t v = (uint32_t)n;
    int leading = 0;
    if (v == 0) return -1;
    while ((v & 0x80000000u) == 0) { v <<= 1; ++leading; }
    return 31 - leading;
}

int32_t mt_random_next_int_bound(MtRandom *r, int32_t until) {
    /* Kotlin's nextInt(from = 0, until). n == until here. */
    int32_t n = until;
    if (n > 0) {
        if ((n & -n) == n) {
            /* A power of two needs no rejection: the top bits are already
             * uniform, so Kotlin takes exactly log2(n) of them. */
            return mt_random_next_bits(r, fast_log2(n));
        }
        int32_t bits, value;
        do {
            bits = (int32_t)(((uint32_t)mt_random_next_int(r)) >> 1);
            value = bits % n;
        } while (bits - value + (n - 1) < 0);
        return value;
    }
    /* Kotlin's other branch, for a range spanning the whole Int domain. It
     * cannot be reached from a deck size, and is written for completeness
     * rather than left to fall through and return something plausible. */
    for (;;) {
        int32_t candidate = mt_random_next_int(r);
        if (candidate >= 0 && candidate < until) return candidate;
    }
}
