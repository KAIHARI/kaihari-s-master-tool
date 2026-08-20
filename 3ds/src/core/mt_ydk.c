#include "mt_ydk.h"

#include <stdio.h>
#include <string.h>

static bool is_space(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n'
        || c == '\v' || c == '\f';
}

/** Trims in place by moving the two ends, the way Kotlin's trim() does. */
static void trim(const char **begin, const char **end) {
    while (*begin < *end && is_space(**begin)) ++(*begin);
    while (*end > *begin && is_space(*(*end - 1))) --(*end);
}

static bool slice_eq_ci(const char *b, const char *e, const char *lit) {
    size_t n = (size_t)(e - b);
    if (strlen(lit) != n) return false;
    for (size_t i = 0; i < n; ++i) {
        char c = b[i];
        if (c >= 'A' && c <= 'Z') c = (char)(c - 'A' + 'a');
        if (c != lit[i]) return false;
    }
    return true;
}

static bool slice_starts_with_ci(const char *b, const char *e, const char *lit) {
    size_t n = (size_t)(e - b);
    size_t m = strlen(lit);
    if (m > n) return false;
    for (size_t i = 0; i < m; ++i) {
        char c = b[i];
        if (c >= 'A' && c <= 'Z') c = (char)(c - 'A' + 'a');
        if (c != lit[i]) return false;
    }
    return true;
}

bool mt_card_id_parse(const char *begin, const char *end, int *out) {
    if (begin >= end) return false;
    long value = 0;
    for (const char *p = begin; p < end; ++p) {
        if (*p < '0' || *p > '9') return false;
        value = value * 10 + (*p - '0');
        /* A passcode is eight digits. Anything that overruns an int is not one,
         * and Kotlin's toIntOrNull returns null on the same input rather than
         * wrapping - so this refuses too, instead of storing a negative id. */
        if (value > 2147483647L) return false;
    }
    *out = (int)value;
    return true;
}

static void warn(MtYdkDocument *doc, const char *fmt, const char *arg) {
    if (doc->warning_count >= MT_YDK_MAX_WARNINGS) {
        ++doc->warnings_dropped;
        return;
    }
    snprintf(doc->warnings[doc->warning_count], MT_YDK_WARNING_LEN, fmt, arg);
    ++doc->warning_count;
}

bool mt_ydk_payload_looks_like_json(const char *begin, size_t len) {
    if (len < 2) return false;
    if (begin[0] != '{' || begin[len - 1] != '}') return false;

    int depth = 0;
    bool in_string = false;
    bool escaped = false;

    for (size_t i = 0; i < len; ++i) {
        char c = begin[i];
        if (in_string) {
            if (escaped)            escaped = false;
            else if (c == '\\')     escaped = true;
            else if (c == '"')      in_string = false;
            continue;
        }
        switch (c) {
            case '"': in_string = true; break;
            case '{': case '[': ++depth; break;
            case '}': case ']':
                if (--depth < 0) return false;   /* closed more than was opened */
                break;
            default: break;
        }
    }
    return depth == 0 && !in_string;
}

static void push_card(MtYdkDocument *doc, MtDeckSection section, int id) {
    switch (section) {
        case MT_SECTION_MAIN:
            if (doc->deck.main_count < MT_DECK_MAIN_MAX) doc->deck.main[doc->deck.main_count++] = id;
            else warn(doc, "Main deck is longer than %s cards; the rest was dropped.", "80");
            break;
        case MT_SECTION_EXTRA:
            if (doc->deck.extra_count < MT_DECK_EXTRA_MAX) doc->deck.extra[doc->deck.extra_count++] = id;
            else warn(doc, "Extra deck is longer than %s cards; the rest was dropped.", "32");
            break;
        case MT_SECTION_SIDE:
            if (doc->deck.side_count < MT_DECK_SIDE_MAX) doc->deck.side[doc->deck.side_count++] = id;
            else warn(doc, "Side deck is longer than %s cards; the rest was dropped.", "32");
            break;
    }
}

void mt_ydk_parse(const char *text, size_t len, MtYdkDocument *out) {
    memset(out, 0, sizeof *out);

    const char *p = text;
    const char *end = text + len;

    /* A UTF-8 BOM, which several Windows editors add and none of them mention. */
    if ((size_t)(end - p) >= 3 &&
        (unsigned char)p[0] == 0xEF && (unsigned char)p[1] == 0xBB &&
        (unsigned char)p[2] == 0xBF) {
        p += 3;
    }

    /* Files with no marker at all are still decklists; leading ids are main. */
    MtDeckSection current = MT_SECTION_MAIN;

    while (p < end) {
        const char *nl = memchr(p, '\n', (size_t)(end - p));
        const char *line_end = nl ? nl : end;
        const char *next = nl ? nl + 1 : end;

        const char *b = p;
        const char *e = line_end;
        trim(&b, &e);          /* also strips the CR of a CRLF file */
        p = next;

        if (b == e) continue;

        if (*b == '#' || *b == '!') {
            const char *mb = b + 1;
            const char *me = e;
            trim(&mb, &me);

            if (slice_eq_ci(mb, me, "main")) {
                current = MT_SECTION_MAIN;
            } else if (slice_eq_ci(mb, me, "extra")) {
                current = MT_SECTION_EXTRA;
            } else if (slice_eq_ci(mb, me, "side")) {
                current = MT_SECTION_SIDE;
            } else if (slice_eq_ci(mb, me, "ydkx-extended")) {
                /* Everything from here on is one JSON document. */
                const char *pb = p;
                const char *pe = end;
                trim(&pb, &pe);
                if (pb < pe && mt_ydk_payload_looks_like_json(pb, (size_t)(pe - pb))) {
                    out->extended = pb;
                    out->extended_len = (size_t)(pe - pb);
                } else if (pb < pe) {
                    warn(out, "The #ydkx-extended block could not be read and was dropped.%s", "");
                }
                p = end;
            } else if (slice_starts_with_ci(mb, me, "created by")) {
                /*
                 * Faithful to the Kotlin, quirk included. It lowercases only to
                 * *test* the prefix and then strips "created by" from the
                 * original, case-sensitively - so "#Created By kai" matches
                 * here and keeps the whole of "Created By kai" as the name.
                 * Reproduced rather than corrected, because the golden vectors
                 * are what the tablet does, not what it ought to do.
                 */
                const char *cb = mb;
                const char *ce = me;
                if ((size_t)(ce - cb) >= 10 && memcmp(cb, "created by", 10) == 0) cb += 10;
                trim(&cb, &ce);
                size_t n = (size_t)(ce - cb);
                if (n > 0) {
                    if (n >= sizeof out->created_by) n = sizeof out->created_by - 1;
                    memcpy(out->created_by, cb, n);
                    out->created_by[n] = '\0';
                }
            }
            /* Any other #... line is a comment and is intentionally ignored. */
            continue;
        }

        int id = 0;
        if (mt_card_id_parse(b, e, &id)) {
            push_card(out, current, id);
        } else {
            char snippet[61];
            size_t n = (size_t)(e - b);
            if (n > 60) n = 60;
            memcpy(snippet, b, n);
            snippet[n] = '\0';
            warn(out, "Skipped unrecognised line: \"%s\"", snippet);
        }
    }
}

/* A tiny appender that keeps counting past the end, like snprintf. */
typedef struct { char *buf; size_t cap; size_t len; } Out;

static void out_str(Out *o, const char *s) {
    size_t n = strlen(s);
    for (size_t i = 0; i < n; ++i) {
        if (o->len + 1 < o->cap) o->buf[o->len] = s[i];
        ++o->len;
    }
}

static void out_bytes(Out *o, const char *s, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        if (o->len + 1 < o->cap) o->buf[o->len] = s[i];
        ++o->len;
    }
}

static void out_int(Out *o, int value) {
    char tmp[16];
    snprintf(tmp, sizeof tmp, "%d", value);
    out_str(o, tmp);
}

static void out_section(Out *o, const char *marker, const int *ids, int count) {
    out_str(o, marker);
    out_str(o, "\n");
    for (int i = 0; i < count; ++i) {
        out_int(o, ids[i]);
        out_str(o, "\n");
    }
}

size_t mt_ydk_write(const MtYdkDocument *doc, char *buffer, size_t cap) {
    Out o = { buffer, cap, 0 };

    if (doc->created_by[0] != '\0') {
        out_str(&o, "#created by ");
        out_str(&o, doc->created_by);
        out_str(&o, "\n");
    }

    out_section(&o, "#main",  doc->deck.main,  doc->deck.main_count);
    out_section(&o, "#extra", doc->deck.extra, doc->deck.extra_count);
    out_section(&o, "!side",  doc->deck.side,  doc->deck.side_count);

    if (doc->extended != NULL) {
        out_str(&o, "#ydkx-extended\n");
        out_bytes(&o, doc->extended, doc->extended_len);
        out_str(&o, "\n");
    }

    if (cap > 0) buffer[o.len < cap ? o.len : cap - 1] = '\0';
    return o.len;
}
