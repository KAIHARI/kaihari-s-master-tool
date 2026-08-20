/*
 * Reading and writing `.ydk` / `.ydkx` - the C form of `core/ydk/YdkCodec.kt`.
 *
 * Parsing is permissive on purpose, because deck files come from many editors:
 * section markers may use `#` or `!`, may appear in any order, may be missing
 * entirely, and files routinely carry CRLF endings or a UTF-8 BOM. Anything
 * unrecognised is a warning rather than a failure - losing a whole decklist
 * because one line was odd is the worse outcome, and that is even more true on
 * a console where the user cannot open a text editor to find out why.
 *
 * ## The extended payload is kept as bytes
 *
 * `#ydkx-extended` carries a JSON object the desktop tool owns: siding
 * patterns, per-card notes, saved view configurations. The Kotlin parses it
 * into a JsonObject and re-serialises it, which preserves every key but not the
 * exact bytes. This keeps the raw slice and writes it back verbatim, which is
 * strictly more preserving and needs no JSON parser on a 268MHz CPU.
 *
 * What it does need is agreement with the Kotlin about whether a payload is
 * *readable at all*, since that decides whether it survives or is dropped with
 * a warning. `mt_ydk_payload_looks_like_json` is a structural check - balanced
 * braces and brackets, strings and escapes respected - not a parser. A payload
 * that is balanced but not valid JSON would be kept here and dropped there.
 * That is the one known divergence and it is deliberate: on this side, keeping
 * bytes we cannot interpret costs nothing and losing them is irreversible.
 */
#ifndef MT_YDK_H
#define MT_YDK_H

#include <stdbool.h>
#include <stddef.h>

/*
 * Caps, not rules. The game says main is 40-60 and extra and side are 0-15, but
 * a *file* may say anything, and a parser that enforced the rules would refuse
 * to open a deck the user is in the middle of building. These are the point at
 * which a file stops being plausible and starts being a runaway.
 */
#define MT_DECK_MAIN_MAX  80
#define MT_DECK_EXTRA_MAX 32
#define MT_DECK_SIDE_MAX  32

#define MT_YDK_MAX_WARNINGS 8
#define MT_YDK_WARNING_LEN  96

typedef enum {
    MT_SECTION_MAIN = 0,
    MT_SECTION_EXTRA,
    MT_SECTION_SIDE
} MtDeckSection;

typedef struct {
    int main[MT_DECK_MAIN_MAX];
    int extra[MT_DECK_EXTRA_MAX];
    int side[MT_DECK_SIDE_MAX];
    int main_count;
    int extra_count;
    int side_count;
} MtDeck;

typedef struct {
    MtDeck deck;

    /** `#created by ...`, empty when absent. */
    char created_by[64];

    /*
     * The `#ydkx-extended` payload, as a slice of the caller's own buffer.
     * Points into the text passed to mt_ydk_parse and is only valid while that
     * buffer is. NULL when the file is a plain .ydk.
     */
    const char *extended;
    size_t extended_len;

    char warnings[MT_YDK_MAX_WARNINGS][MT_YDK_WARNING_LEN];
    int warning_count;
    /** Warnings past the cap are counted but not stored. */
    int warnings_dropped;
} MtYdkDocument;

static inline bool mt_ydk_is_ydkx(const MtYdkDocument *doc) {
    return doc->extended != NULL;
}

/** Parses a deck file. Never fails; unreadable lines become warnings. */
void mt_ydk_parse(const char *text, size_t len, MtYdkDocument *out);

/** True if `line` is only digits, i.e. a Konami passcode. Empty is false. */
bool mt_card_id_parse(const char *begin, const char *end, int *out);

/** The structural check described in this file's header comment. */
bool mt_ydk_payload_looks_like_json(const char *begin, size_t len);

/**
 * Serialises a deck into `buffer`, returning the length that was needed.
 *
 * Returns the full required length even when it exceeds `cap` - the caller can
 * size a buffer from a first call, as with snprintf. The output is always NUL
 * terminated when cap > 0.
 *
 * Sections are written in YGOPro's canonical #main / #extra / !side order so the
 * output imports cleanly into EDOPro and every other editor. Parsing is
 * order-independent, so a round trip with the desktop tool - which writes side
 * before extra - stays lossless.
 */
size_t mt_ydk_write(const MtYdkDocument *doc, char *buffer, size_t cap);

#endif /* MT_YDK_H */
