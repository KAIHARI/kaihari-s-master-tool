/*
 * The conformance suite: the C port, asserted against the Kotlin it was
 * translated from.
 *
 * This links no libctru and runs on the host, which is the point — a regression
 * in the port is caught by `make -C 3ds/test` on any machine, in under a second,
 * without a console or an emulator or the devkitARM toolchain. It is the direct
 * analogue of the rule in CLAUDE.md that keeps :core compiling in a sandbox
 * while :ui only compiles in CI.
 *
 * The vectors in `vectors/` are written by `GoldenVectorExportTest` in
 * `:core`'s jvmTest source set and are committed. If this suite fails, either
 * the C is wrong or the Kotlin moved; `git log` on the vector file says which.
 */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../src/core/mt_board_layout.h"
#include "../src/core/mt_drop.h"
#include "../src/core/mt_handfan.h"
#include "../src/core/mt_playfield.h"
#include "../src/core/mt_random.h"
#include "../src/core/mt_spring.h"
#include "../src/core/mt_ydk.h"
#include "../src/core/mt_types.h"

static int g_checks = 0;
static int g_failures = 0;
static const char *g_file = "";
static int g_line = 0;

/*
 * Absolute plus relative, and both are loose enough to absorb a difference in
 * float operation order and tight enough to catch a real one. A board pixel is
 * 1.0, so 1e-3 is a thousandth of the smallest thing anybody can see.
 */
static int close_enough(float a, float b) {
    if (isnan(a) && isnan(b)) return 1;
    return fabsf(a - b) <= 1e-3f + 1e-5f * fabsf(b);
}

static void check_f(const char *what, float got, float want) {
    ++g_checks;
    if (!close_enough(got, want)) {
        ++g_failures;
        if (g_failures <= 25) {
            fprintf(stderr, "%s:%d: %s got %.9g want %.9g\n",
                    g_file, g_line, what, (double)got, (double)want);
        }
    }
}

/*
 * Exact, and separate from check_f on purpose.
 *
 * check_f's tolerance is absolute-plus-relative, which is right for a board
 * coordinate and wrong for anything counted. At a card id of 46,986,414 the
 * relative term alone is a tolerance of 470 - so a passcode off by a few
 * hundred would have passed - and at a raw PRNG output near 2^31 it is about
 * 19,000. A float cannot even represent a 32-bit integer exactly. Integers are
 * compared as integers.
 */
static void check_i(const char *what, long long got, long long want) {
    ++g_checks;
    if (got != want) {
        ++g_failures;
        if (g_failures <= 25) {
            fprintf(stderr, "%s:%d: %s got %lld want %lld\n", g_file, g_line, what, got, want);
        }
    }
}

static void check_s(const char *what, const char *got, const char *want) {
    ++g_checks;
    if (strcmp(got, want) != 0) {
        ++g_failures;
        if (g_failures <= 25) {
            fprintf(stderr, "%s:%d: %s got '%s' want '%s'\n",
                    g_file, g_line, what, got, want);
        }
    }
}

/* ---- the stable spelling of a slot, shared with the Kotlin by convention -- */

static const char *slot_name(MtBoardSlot s) {
    static char buf[8];
    switch (s.kind) {
        case MT_SLOT_DECK:       return "DECK";
        case MT_SLOT_EXTRA_DECK: return "EXTRA";
        case MT_SLOT_GRAVEYARD:  return "GY";
        case MT_SLOT_BANISHED:   return "BAN";
        case MT_SLOT_ZONE:
            switch (s.zone.kind) {
                case MT_ZONE_MONSTER:       snprintf(buf, sizeof buf, "M%d", s.zone.index); return buf;
                case MT_ZONE_EXTRA_MONSTER: snprintf(buf, sizeof buf, "E%d", s.zone.index); return buf;
                case MT_ZONE_SPELL_TRAP:    snprintf(buf, sizeof buf, "S%d", s.zone.index); return buf;
                case MT_ZONE_FIELD_SPELL:   return "F";
            }
            return "?";
    }
    return "?";
}

static const char *position_name(MtCardPosition p) {
    switch (p) {
        case MT_POS_FACE_UP_ATK:   return "FACE_UP_ATK";
        case MT_POS_FACE_UP_DEF:   return "FACE_UP_DEF";
        case MT_POS_FACE_DOWN_DEF: return "FACE_DOWN_DEF";
        case MT_POS_FACE_DOWN_ATK: return "FACE_DOWN_ATK";
        default:                   return "?";
    }
}

/* ---- reading the vector files ------------------------------------------ */

static FILE *open_vectors(const char *name) {
    char path[512];
    snprintf(path, sizeof path, "vectors/%s", name);
    FILE *f = fopen(path, "r");
    if (!f) {
        fprintf(stderr, "cannot open %s - run :core:jvmTest to generate it\n", path);
        exit(2);
    }
    g_file = name;
    g_line = 0;
    return f;
}

/** Splits a line on whitespace in place. Returns the field count. */
static int split(char *line, char **out, int max) {
    int n = 0;
    char *save = NULL;
    for (char *tok = strtok_r(line, " \t\r\n", &save);
         tok && n < max;
         tok = strtok_r(NULL, " \t\r\n", &save)) {
        out[n++] = tok;
    }
    return n;
}

static int is_skippable(const char *line) {
    while (*line == ' ' || *line == '\t') ++line;
    return *line == '#' || *line == '\n' || *line == '\r' || *line == '\0';
}

/* ---- the cases --------------------------------------------------------- */

static int test_board_solve(void) {
    FILE *f = open_vectors("board_solve.txt");
    char line[2048];
    char *v[40];
    int rows = 0;

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 40);
        if (n != 26) { fprintf(stderr, "%s:%d: expected 26 fields, got %d\n", g_file, g_line, n); ++g_failures; continue; }

        MtBoardLayout l = mt_board_solve((float)atof(v[0]), (float)atof(v[1]),
                                         (float)atof(v[2]), (float)atof(v[3]),
                                         (float)atof(v[4]));
        /* v[5] is "=" */
        check_f("cardWidth",  l.card_width,  (float)atof(v[6]));
        check_f("cardHeight", l.card_height, (float)atof(v[7]));
        check_f("gap",        l.gap,         (float)atof(v[8]));
        check_i("fits",       l.fits ? 1 : 0, atoll(v[9]));

        const MtSlot rects[4] = { l.field, l.hand, l.readout, mt_board_bounds(&l) };
        const char *names[4]  = { "field", "hand", "readout", "bounds" };
        for (int i = 0; i < 4; ++i) {
            int base = 10 + i * 4;
            char what[32];
            snprintf(what, sizeof what, "%s.left",   names[i]); check_f(what, rects[i].left,   (float)atof(v[base + 0]));
            snprintf(what, sizeof what, "%s.top",    names[i]); check_f(what, rects[i].top,    (float)atof(v[base + 1]));
            snprintf(what, sizeof what, "%s.width",  names[i]); check_f(what, rects[i].width,  (float)atof(v[base + 2]));
            snprintf(what, sizeof what, "%s.height", names[i]); check_f(what, rects[i].height, (float)atof(v[base + 3]));
        }
        ++rows;
    }
    fclose(f);
    printf("  board_solve      %4d cases\n", rows);
    return rows;
}

static int test_board_slots(void) {
    FILE *f = open_vectors("board_slots.txt");
    MtBoardLayout l = mt_board_solve(400.0f, 240.0f, 0.686f, 1.0f, 0.0f);
    char line[512];
    char *v[8];
    int index = 0;

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 8);
        if (n != 6) { fprintf(stderr, "%s:%d: expected 6 fields, got %d\n", g_file, g_line, n); ++g_failures; continue; }

        if (index >= l.slot_count) {
            fprintf(stderr, "%s:%d: more slots in the vectors than in the C\n", g_file, g_line);
            ++g_failures;
            break;
        }
        /* Index-by-index, not name lookup: the order is what slotAt resolves
         * ties by, so a port that produced the right seventeen rectangles in
         * the wrong order must fail here. */
        check_s("slot order", slot_name(l.slots[index].slot), v[0]);
        check_f("slot.left",   l.slots[index].rect.left,   (float)atof(v[2]));
        check_f("slot.top",    l.slots[index].rect.top,    (float)atof(v[3]));
        check_f("slot.width",  l.slots[index].rect.width,  (float)atof(v[4]));
        check_f("slot.height", l.slots[index].rect.height, (float)atof(v[5]));
        ++index;
    }
    fclose(f);
    if (index != l.slot_count) {
        fprintf(stderr, "%s: %d slots in the vectors, %d in the C\n", g_file, index, l.slot_count);
        ++g_failures;
    }
    printf("  board_slots      %4d slots\n", index);
    return index;
}

static int test_board_slot_at(void) {
    FILE *f = open_vectors("board_slot_at.txt");
    MtBoardLayout l = mt_board_solve(400.0f, 240.0f, 0.686f, 1.0f, 0.0f);
    char line[512];
    char *v[8];
    int rows = 0;

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 8);
        if (n != 4) { fprintf(stderr, "%s:%d: expected 4 fields, got %d\n", g_file, g_line, n); ++g_failures; continue; }

        const MtPlacedSlot *hit = mt_board_slot_at(&l, (float)atof(v[0]), (float)atof(v[1]));
        check_s("slotAt", hit ? slot_name(hit->slot) : "-", v[3]);
        ++rows;
    }
    fclose(f);
    printf("  board_slot_at    %4d points\n", rows);
    return rows;
}

static int parse_intent(const char *name, MtDropIntent *out, int *present) {
    MtMatPoint centre = { 0.5f, 0.5f };
    *present = 1;
    if (!strcmp(name, "none"))      { *present = 0; return 1; }
    if (!strcmp(name, "free"))      { *out = mt_drop_free(centre); return 1; }
    if (!strcmp(name, "monster"))   { *out = mt_drop_zone(mt_slot_zone(mt_zone(MT_ZONE_MONSTER, 2)), centre); return 1; }
    if (!strcmp(name, "extra"))     { *out = mt_drop_zone(mt_slot_zone(mt_zone(MT_ZONE_EXTRA_MONSTER, 0)), centre); return 1; }
    if (!strcmp(name, "spell"))     { *out = mt_drop_zone(mt_slot_zone(mt_zone(MT_ZONE_SPELL_TRAP, 1)), centre); return 1; }
    if (!strcmp(name, "field"))     { *out = mt_drop_zone(mt_slot_zone(mt_zone(MT_ZONE_FIELD_SPELL, 0)), centre); return 1; }
    if (!strcmp(name, "deckslot"))  { *out = mt_drop_zone(mt_slot_pile(MT_SLOT_DECK), centre); return 1; }
    if (!strcmp(name, "stack"))     { *out = mt_drop_simple(MT_DROP_STACK); out->target = 7; return 1; }
    if (!strcmp(name, "hand"))      { *out = mt_drop_simple(MT_DROP_HAND); out->target = 2; return 1; }
    if (!strcmp(name, "graveyard")) { *out = mt_drop_simple(MT_DROP_GRAVEYARD); return 1; }
    if (!strcmp(name, "cancel"))    { *out = mt_drop_simple(MT_DROP_CANCEL); return 1; }
    return 0;
}

static int test_set_position(void) {
    FILE *f = open_vectors("set_position.txt");
    char line[512];
    char *v[8];
    int rows = 0;

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 8);
        if (n != 6) { fprintf(stderr, "%s:%d: expected 6 fields, got %d\n", g_file, g_line, n); ++g_failures; continue; }

        MtDropIntent intent;
        int present = 0;
        if (!parse_intent(v[2], &intent, &present)) {
            fprintf(stderr, "%s:%d: unknown intent '%s'\n", g_file, g_line, v[2]);
            ++g_failures;
            continue;
        }
        MtMonsterHint hint = !strcmp(v[3], "yes") ? MT_MONSTER_YES
                           : !strcmp(v[3], "no")  ? MT_MONSTER_NO
                                                  : MT_MONSTER_UNKNOWN;

        MtCardPosition got = mt_set_position(atoi(v[0]) != 0, atoi(v[1]) != 0,
                                             present ? &intent : NULL, hint);
        check_s("setPosition", position_name(got), v[5]);
        ++rows;
    }
    fclose(f);
    printf("  set_position     %4d cases\n", rows);
    return rows;
}

static int test_spring(void) {
    FILE *f = open_vectors("spring.txt");
    char line[512];
    char *v[16];
    int rows = 0;

    /*
     * A trajectory is replayed, not sampled. The state carries between rows of
     * the same run, so a bug in the integrator shows up as drift that grows
     * down the file rather than as one wrong number - which is exactly how an
     * explicit-Euler mistake would present.
     */
    MtSpringValue state = { 0.0f, 0.0f };

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 16);
        if (n != 10) { fprintf(stderr, "%s:%d: expected 10 fields, got %d\n", g_file, g_line, n); ++g_failures; continue; }

        MtSpringSpec spec;
        if      (!strcmp(v[0], "snappy")) spec = mt_spring_snappy();
        else if (!strcmp(v[0], "bouncy")) spec = mt_spring_bouncy();
        else if (!strcmp(v[0], "calm"))   spec = mt_spring_calm();
        else { fprintf(stderr, "%s:%d: unknown spec '%s'\n", g_file, g_line, v[0]); ++g_failures; continue; }

        float v0     = (float)atof(v[1]);
        float vel0   = (float)atof(v[2]);
        float target = (float)atof(v[3]);
        float dt     = (float)atof(v[4]);
        int   step   = atoi(v[5]);

        if (step == 0) { state.value = v0; state.velocity = vel0; }
        state = mt_spring_step(state, target, spec, dt);

        check_f("spring.value",    state.value,    (float)atof(v[7]));
        check_f("spring.velocity", state.velocity, (float)atof(v[8]));
        check_i("spring.settled",
                mt_spring_settled(state, target, 0.5f, 0.5f) ? 1 : 0,
                atoll(v[9]));
        ++rows;
    }
    fclose(f);
    printf("  spring           %4d steps\n", rows);
    return rows;
}

/* ---- deck files -------------------------------------------------------- */

static char *slurp(const char *path, size_t *len) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (n < 0) { fclose(f); return NULL; }
    char *buf = malloc((size_t)n + 1);
    if (!buf) { fclose(f); return NULL; }
    size_t got = fread(buf, 1, (size_t)n, f);
    fclose(f);
    buf[got] = '\0';
    *len = got;
    return buf;
}

/*
 * The label says where the input came from: `case/` for the synthetic files
 * written beside the vectors, `repo/` for real decks at the repository root.
 * The prefixes exist because two different decks in this repository are both
 * called lab.ydkx, and a bare file name made them one key.
 */
static void resolve_deck_path(const char *label, char *out, size_t cap) {
    if (strncmp(label, "case/", 5) == 0)      snprintf(out, cap, "vectors/%s", label);
    else if (strncmp(label, "repo/", 5) == 0) snprintf(out, cap, "../../%s", label + 5);
    else                                      snprintf(out, cap, "%s", label);
}

static int test_ydk(void) {
    FILE *f = open_vectors("ydk.txt");
    char line[8192];
    char *v[512];
    int rows = 0;

    char loaded_label[256] = "";
    char *text = NULL;
    size_t text_len = 0;
    MtYdkDocument doc;
    memset(&doc, 0, sizeof doc);

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;

        /* `created` takes the rest of the line, so it is read before the line
         * is chopped into fields - a name may contain spaces. */
        char raw[8192];
        snprintf(raw, sizeof raw, "%s", line);
        char *nl = strpbrk(raw, "\r\n");
        if (nl) *nl = '\0';

        int n = split(line, v, 512);
        if (n < 2) { fprintf(stderr, "%s:%d: short line\n", g_file, g_line); ++g_failures; continue; }

        const char *label = v[0];
        const char *key = v[1];

        if (strcmp(label, loaded_label) != 0) {
            free(text);
            char path[512];
            resolve_deck_path(label, path, sizeof path);
            text = slurp(path, &text_len);
            if (!text) {
                fprintf(stderr, "%s:%d: cannot read %s\n", g_file, g_line, path);
                ++g_failures;
                loaded_label[0] = '\0';
                continue;
            }
            mt_ydk_parse(text, text_len, &doc);
            snprintf(loaded_label, sizeof loaded_label, "%s", label);
        }

        if (!strcmp(key, "ydkx")) {
            check_i("ydkx", mt_ydk_is_ydkx(&doc) ? 1 : 0, atoll(v[2]));
        } else if (!strcmp(key, "warnings")) {
            check_i("warnings", doc.warning_count, atoll(v[2]));
        } else if (!strcmp(key, "created")) {
            /* Everything after "<label> created " on the untouched line. */
            const char *at = strstr(raw, " created ");
            const char *want = at ? at + 9 : "-";
            const char *got = doc.created_by[0] ? doc.created_by : "-";
            check_s("createdBy", got, want);
        } else {
            const int *ids; int count;
            if      (!strcmp(key, "main"))  { ids = doc.deck.main;  count = doc.deck.main_count; }
            else if (!strcmp(key, "extra")) { ids = doc.deck.extra; count = doc.deck.extra_count; }
            else if (!strcmp(key, "side"))  { ids = doc.deck.side;  count = doc.deck.side_count; }
            else { fprintf(stderr, "%s:%d: unknown key '%s'\n", g_file, g_line, key); ++g_failures; continue; }

            int want_count = atoi(v[2]);
            check_i("section count", count, want_count);
            for (int i = 0; i < want_count && 3 + i < n; ++i) {
                char what[32];
                snprintf(what, sizeof what, "%s[%d]", key, i);
                check_i(what, i < count ? ids[i] : -1, atoll(v[3 + i]));
            }
        }
        ++rows;
    }
    free(text);
    fclose(f);
    printf("  ydk              %4d records\n", rows);
    return rows;
}

static int test_random(void) {
    FILE *f = open_vectors("random.txt");
    char line[256];
    char *v[8];
    int rows = 0;

    /*
     * Each block restarts the generator from its seed, so a wrong number of
     * discarded outputs at seeding shows up on the first row of a block rather
     * than as drift much later.
     */
    MtRandom r;
    long long loaded_seed = 0;
    int loaded_bound = -1;
    int loaded_raw = 0;
    int have = 0;

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 8);

        if (!strcmp(v[0], "raw")) {
            if (n != 5) { fprintf(stderr, "%s:%d: expected 5 fields\n", g_file, g_line); ++g_failures; continue; }
            long long seed = atoll(v[1]);
            int i = atoi(v[2]);
            if (!have || !loaded_raw || seed != loaded_seed || i == 0) {
                mt_random_seed(&r, (int64_t)seed);
                loaded_seed = seed; loaded_raw = 1; loaded_bound = -1; have = 1;
            }
            check_i("random.nextInt", mt_random_next_int(&r), atoll(v[4]));
        } else if (!strcmp(v[0], "bound")) {
            if (n != 6) { fprintf(stderr, "%s:%d: expected 6 fields\n", g_file, g_line); ++g_failures; continue; }
            long long seed = atoll(v[1]);
            int bound = atoi(v[2]);
            int i = atoi(v[3]);
            if (!have || loaded_raw || seed != loaded_seed || bound != loaded_bound || i == 0) {
                mt_random_seed(&r, (int64_t)seed);
                loaded_seed = seed; loaded_bound = bound; loaded_raw = 0; have = 1;
            }
            check_i("random.nextInt(bound)",
                    mt_random_next_int_bound(&r, bound), atoll(v[5]));
        } else {
            fprintf(stderr, "%s:%d: unknown row '%s'\n", g_file, g_line, v[0]);
            ++g_failures;
            continue;
        }
        ++rows;
    }
    fclose(f);
    printf("  random           %4d draws\n", rows);
    return rows;
}

/* ---- the table --------------------------------------------------------- */

static MtCardPosition position_arg(int raw) {
    return (raw >= (int)MT_POS_COUNT) ? MT_POS_KEEP : (MtCardPosition)raw;
}

/** Applies one script line. Returns what the operation answered. */
static bool apply_op(MtPlayField *f, char **v, int n) {
    const char *op = v[2];   /* v[0] = step, v[1] = "op" */
#define ARG_I(k) atoi(v[2 + (k)])
#define ARG_F(k) ((float)atof(v[2 + (k)]))
#define ARG_AT(k) ((MtMatPoint){ ARG_F(k), ARG_F((k) + 1) })
    (void)n;

    if (!strcmp(op, "shuffle"))       { mt_field_shuffle_deck(f, atoll(v[3])); return true; }
    if (!strcmp(op, "draw"))          return mt_field_draw(f);
    if (!strcmp(op, "playhand"))      return mt_field_play_from_hand(f, ARG_I(1), ARG_AT(2), position_arg(ARG_I(4)));
    if (!strcmp(op, "playdeck"))      return mt_field_play_from_deck(f, ARG_I(1), ARG_AT(2), position_arg(ARG_I(4)));
    if (!strcmp(op, "playextra"))     return mt_field_play_from_extra(f, ARG_I(1), ARG_AT(2), position_arg(ARG_I(4)));
    if (!strcmp(op, "playgy"))        return mt_field_play_from_graveyard(f, ARG_I(1), ARG_AT(2), position_arg(ARG_I(4)));
    if (!strcmp(op, "playban"))       return mt_field_play_from_banished(f, ARG_I(1), ARG_AT(2), position_arg(ARG_I(4)));
    if (!strcmp(op, "move"))          return mt_field_move_on_mat(f, ARG_I(1), ARG_AT(2), position_arg(ARG_I(4)));
    if (!strcmp(op, "stack"))         return mt_field_stack_onto(f, ARG_I(1), ARG_I(2), position_arg(ARG_I(3)));
    if (!strcmp(op, "unstack"))       return mt_field_unstack(f, ARG_I(1), ARG_AT(2));
    if (!strcmp(op, "front"))         return mt_field_bring_to_front(f, ARG_I(1));
    if (!strcmp(op, "flip"))          return mt_field_flip(f, ARG_I(1));
    if (!strcmp(op, "rotate"))        return mt_field_rotate(f, ARG_I(1));
    if (!strcmp(op, "setpos"))        return mt_field_set_position(f, ARG_I(1), position_arg(ARG_I(2)));
    if (!strcmp(op, "gy"))            return mt_field_to_graveyard(f, ARG_I(1));
    if (!strcmp(op, "banish"))        return mt_field_to_banish(f, ARG_I(1), ARG_I(2) != 0);
    if (!strcmp(op, "tohand"))        return mt_field_to_hand(f, ARG_I(1), ARG_I(2));
    if (!strcmp(op, "decktop"))       return mt_field_to_deck_top(f, ARG_I(1));
    if (!strcmp(op, "deckbottom"))    return mt_field_to_deck_bottom(f, ARG_I(1));
    if (!strcmp(op, "toextra"))       return mt_field_to_extra_deck(f, ARG_I(1));
    if (!strcmp(op, "handgy"))        return mt_field_hand_to_graveyard(f, ARG_I(1));
    if (!strcmp(op, "handbanish"))    return mt_field_hand_to_banish(f, ARG_I(1));
    if (!strcmp(op, "handdecktop"))   return mt_field_hand_to_deck_top(f, ARG_I(1));
    if (!strcmp(op, "handdeckbottom"))return mt_field_hand_to_deck_bottom(f, ARG_I(1));
    if (!strcmp(op, "reorder"))       return mt_field_reorder_hand(f, ARG_I(1), ARG_I(2));
    if (!strcmp(op, "counter"))       return mt_field_add_counter(f, ARG_I(1), ARG_I(2));
    if (!strcmp(op, "attach"))        return mt_field_attach_as_material(f, ARG_I(1), ARG_I(2));
    if (!strcmp(op, "detach"))        return mt_field_detach_material(f, ARG_I(1));
    if (!strcmp(op, "takefromunder")) return mt_field_take_from_under(f, ARG_I(1), ARG_I(2), ARG_AT(3), position_arg(ARG_I(5)));
    if (!strcmp(op, "life"))          { mt_field_adjust_life(f, ARG_I(1)); return true; }
    if (!strcmp(op, "phase"))         { mt_field_next_phase(f); return true; }
    if (!strcmp(op, "endturn"))       { mt_field_end_turn(f); return true; }

    fprintf(stderr, "%s:%d: unknown op '%s'\n", g_file, g_line, op);
    ++g_failures;
    return false;
#undef ARG_AT
#undef ARG_F
#undef ARG_I
}

/*
 * A pile, as id/position/counters triples.
 *
 * Ids alone are not enough, and that was a real hole: a card's position and
 * counters travel with it into a pile, and recording only the id made `lift`
 * clearing counters and `toBanish` setting face-down both invisible. A mutation
 * that left counters on a card sent to the graveyard passed cleanly.
 */
static void check_pile(const char *name, const MtPlayField *f,
                       const int *pile, int count, char **v, int n) {
    int want = atoi(v[2]);
    check_i(name, count, want);
    for (int i = 0; i < want && 3 + i * 3 + 2 < n; ++i) {
        char what[64];
        int id = (i < count) ? pile[i] : -1;
        snprintf(what, sizeof what, "%s[%d].id", name, i);
        check_i(what, id, atoll(v[3 + i * 3]));

        int position = (id >= 0) ? (int)f->instances[id].position : -1;
        int counters = (id >= 0) ? f->instances[id].counters : -1;
        snprintf(what, sizeof what, "%s[%d].position", name, i);
        check_i(what, position, atoll(v[4 + i * 3]));
        snprintf(what, sizeof what, "%s[%d].counters", name, i);
        check_i(what, counters, atoll(v[5 + i * 3]));
    }
}

static int test_playfield(void) {
    FILE *f = open_vectors("playfield.txt");
    char line[4096];
    char *v[512];
    int rows = 0;

    /* The same deck the exporter built: distinct passcodes, so a card in the
     * wrong pile is visible rather than plausible. */
    int main_ids[24], extra_ids[6];
    for (int i = 0; i < 24; ++i) main_ids[i] = 1000 + i + 1;
    for (int i = 0; i < 6; ++i)  extra_ids[i] = 9000 + i + 1;

    static MtPlayField field;
    mt_field_set_up(&field, main_ids, 24, extra_ids, 6);
    bool last_ok = true;

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 512);
        if (n < 2) continue;
        const char *key = v[1];

        if (!strcmp(key, "op")) {
            last_ok = apply_op(&field, v, n);
            ++rows;
        } else if (!strcmp(key, "ok")) {
            check_i("op refused/accepted", last_ok ? 1 : 0, atoll(v[2]));
        } else if (!strcmp(key, "lp")) {
            check_i("lifePoints", field.life_points, atoll(v[2]));
            check_i("phase", (int)field.phase, atoll(v[4]));
            check_i("turn", field.turn, atoll(v[6]));
        } else if (!strcmp(key, "mat")) {
            check_i("mat count", field.mat_count, atoll(v[2]));
        } else if (!strncmp(key, "mat", 3)) {
            int index = atoi(key + 3);
            if (index >= field.mat_count) { check_i("mat index present", 0, 1); continue; }
            const MtPlacedCard *placed = &field.mat[index];
            const MtBoardCard *card = &field.instances[placed->card];

            int c = 2;
            check_i("placed.id",      placed->card,        atoll(v[c++]));
            check_i("placed.cardId",  card->card_id,       atoll(v[c++]));
            check_f("placed.x",       placed->at.x,  (float)atof(v[c++]));
            check_f("placed.y",       placed->at.y,  (float)atof(v[c++]));
            check_i("placed.position", (int)card->position, atoll(v[c++]));
            check_i("placed.counters", card->counters,      atoll(v[c++]));

            int beneath = atoi(v[c++]);
            check_i("placed.beneath count", placed->beneath_count, beneath);
            for (int i = 0; i < beneath && c < n; ++i) {
                check_i("beneath", i < placed->beneath_count ? placed->beneath[i] : -1, atoll(v[c++]));
            }
            int materials = (c < n) ? atoi(v[c++]) : 0;
            check_i("placed.material count", card->material_count, materials);
            for (int i = 0; i < materials && c < n; ++i) {
                check_i("material", i < card->material_count ? card->materials[i] : -1, atoll(v[c++]));
            }
        } else if (!strcmp(key, "hand"))  check_pile("hand", &field, field.hand, field.hand_count, v, n);
        else if   (!strcmp(key, "deck"))  check_pile("deck", &field, field.deck, field.deck_count, v, n);
        else if   (!strcmp(key, "extra")) check_pile("extra", &field, field.extra_deck, field.extra_deck_count, v, n);
        else if   (!strcmp(key, "gy"))    check_pile("gy", &field, field.graveyard, field.graveyard_count, v, n);
        else if   (!strcmp(key, "ban"))   check_pile("ban", &field, field.banished, field.banished_count, v, n);
    }
    fclose(f);
    printf("  playfield        %4d ops\n", rows);
    return rows;
}

/* ---- the hand's row ---------------------------------------------------- */

/** Parses "0,3" or "-" into a list. Returns the count. */
static int parse_list(const char *s, int *out, int cap) {
    if (!strcmp(s, "-")) return 0;
    int n = 0;
    int value = 0;
    bool have = false;
    for (const char *c = s; ; ++c) {
        if (*c >= '0' && *c <= '9') { value = value * 10 + (*c - '0'); have = true; }
        else {
            if (have && n < cap) out[n++] = value;
            value = 0; have = false;
            if (*c == '\0') break;
        }
    }
    return n;
}

static int test_handfan(void) {
    FILE *f = open_vectors("handfan.txt");
    MtBoardLayout layout = mt_board_solve(320.0f, 226.0f, 0.686f, 1.0f, 0.0f);
    MtSlot band = layout.hand;
    const float SF = MT_HAND_STEP_FRACTION;

    char line[2048];
    char *v[256];
    int rows = 0;

    /* The row named by the most recent `row` line - every following line is
     * about it, exactly as the exporter emitted them. */
    MtHandRow row = mt_hand_row_of(0);

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 256);
        if (n < 2) continue;
        const char *key = v[0];

        if (!strcmp(key, "row")) {
            int count = atoi(v[1]);
            int lifted[MT_MAX_HAND_PLACES], opening[MT_MAX_HAND_PLACES];
            int lifted_n = parse_list(v[2], lifted, MT_MAX_HAND_PLACES);
            int opening_n = parse_list(v[3], opening, MT_MAX_HAND_PLACES);
            row = mt_hand_row(count, lifted, lifted_n, opening, opening_n);

            /* v[4] is "=" */
            check_i("row size", row.count, atoll(v[5]));
            for (int i = 0; i < row.count && 6 + i < n; ++i) {
                char what[32];
                snprintf(what, sizeof what, "place[%d]", i);
                check_i(what, row.places[i], atoll(v[6 + i]));
            }
            ++rows;
        } else if (!strcmp(key, "step")) {
            check_f("step", mt_hand_step(band, layout.card_width, row.count, SF),
                    (float)atof(v[2]));
        } else if (!strcmp(key, "centre")) {
            int place = atoi(v[2]);
            int places = atoi(v[3]);
            check_f("centreOf",
                    mt_hand_centre_of(band, layout.card_width, place, places, SF),
                    (float)atof(v[4]));
        } else if (!strcmp(key, "insert")) {
            int count = atoi(v[1]);
            float x = (float)atof(v[2]);
            int got = mt_hand_insert_at(band, layout.card_width, &row, count, x, SF);
            check_i("insertAt", got, atoll(v[4]));

            /*
             * The fixed point, asserted here rather than only in Kotlin: when
             * the row is holding a place open for the gap insertAt just named,
             * asking again must name the same gap. A row that re-asked and got
             * a different answer would flicker every frame.
             */
            int opened = mt_hand_opening_for(&row, got, count);
            if (opened >= 0) {
                int again = mt_hand_insert_at(band, layout.card_width, &row, count, x, SF);
                check_i("insertAt is a fixed point", again, got);
            }
        } else if (!strcmp(key, "openingfor")) {
            check_i("openingFor", mt_hand_opening_for(&row, atoi(v[2]), atoi(v[1])), atoll(v[4]));
        } else if (!strcmp(key, "gapafter")) {
            check_i("gapAfter", mt_hand_gap_after(&row, atoi(v[2]), atoi(v[1])), atoll(v[4]));
        } else if (!strcmp(key, "placeof")) {
            check_i("placeOf", mt_hand_place_of(&row, atoi(v[2])), atoll(v[4]));
        } else if (!strcmp(key, "point")) {
            int places = atoi(v[1]);
            int place = atoi(v[2]);
            MtMatPoint p = mt_hand_point_for(&layout, place, places, SF);
            check_f("pointFor.x", p.x, (float)atof(v[3]));
            check_f("pointFor.y", p.y, (float)atof(v[4]));
        }
    }
    fclose(f);
    printf("  handfan          %4d rows\n", rows);
    return rows;
}

/* ---- where a dragged card would land ----------------------------------- */

/** The stable spelling the exporter uses for an intent. */
static void check_intent(const MtDropIntent *got, char **v, int base) {
    check_i("intent.kind", (int)got->kind, atoll(v[base]));
    int target = (got->kind == MT_DROP_STACK || got->kind == MT_DROP_ATTACH ||
                  got->kind == MT_DROP_HAND) ? got->target : -1;
    check_i("intent.target", target, atoll(v[base + 1]));

    if (got->kind == MT_DROP_ZONE) {
        check_s("intent.slot", slot_name(got->slot), v[base + 2]);
    }
    if (got->kind == MT_DROP_FREE || got->kind == MT_DROP_ZONE) {
        check_f("intent.at.x", got->at.x, (float)atof(v[base + 3]));
        check_f("intent.at.y", got->at.y, (float)atof(v[base + 4]));
    }
}

static int test_droptargets(void) {
    FILE *f = open_vectors("droptargets.txt");
    MtBoardLayout layout = mt_board_solve(400.0f, 240.0f, 0.686f, 1.0f, 0.0f);

    static MtPlayField field;
    memset(&field, 0, sizeof field);

    char line[1024];
    char *v[64];
    int rows = 0;

    /* Paths feed each answer back as `previous`, which is the whole of the
     * hysteresis test - so the state has to survive between lines, and reset
     * when a new path begins. */
    MtDropIntent previous;
    bool have_previous = false;
    char path_name[64] = "";
    MtFanHome home = { { 0.22f, 0.28f }, false };
    bool have_home = false;

    MtHandRow hand = mt_hand_row_of(0);

    while (fgets(line, sizeof line, f)) {
        ++g_line;
        if (is_skippable(line)) continue;
        int n = split(line, v, 64);
        if (n < 2) continue;

        if (!strcmp(v[0], "field")) {
            field.hand_count = atoi(v[1]);
            for (int i = 0; i < field.hand_count; ++i) field.hand[i] = 200 + i;
            hand = mt_hand_row_of(field.hand_count);
            field.mat_count = 0;
        } else if (!strcmp(v[0], "card")) {
            int id = atoi(v[1]);
            field.instances[id % MT_MAX_INSTANCES].instance_id = id;
            MtPlacedCard *placed = &field.mat[field.mat_count++];
            placed->card = id;
            placed->at.x = (float)atof(v[2]);
            placed->at.y = (float)atof(v[3]);
            placed->beneath_count = 0;
        } else if (!strcmp(v[0], "grid")) {
            MtMatPoint point = { (float)atof(v[1]), (float)atof(v[2]) };
            bool attaching = atoi(v[3]) != 0;
            MtDropIntent got = mt_drop_resolve(point, 101, &field, &layout,
                                               NULL, attaching, NULL, &hand,
                                               MT_HAND_STEP_FRACTION);
            check_intent(&got, v, 5);
            ++rows;
        } else if (!strcmp(v[0], "home")) {
            /* Announced by the exporter before each path, because a home
             * inferred from the path's *name* on this side resolved against
             * the wrong point the moment a path used a different one - and it
             * read as a port bug rather than as a harness bug. */
            snprintf(path_name, sizeof path_name, "%s", v[1]);
            have_previous = false;
            have_home = strcmp(v[2], "-") != 0;
            if (have_home) {
                home.at.x = (float)atof(v[2]);
                home.at.y = (float)atof(v[3]);
                home.departed = atoi(v[4]) != 0;
            }
        } else if (!strcmp(v[0], "path")) {
            MtMatPoint point = { (float)atof(v[3]), (float)atof(v[4]) };
            MtDropIntent got = mt_drop_resolve(point, 101, &field, &layout,
                                               have_previous ? &previous : NULL,
                                               false,
                                               have_home ? &home : NULL,
                                               &hand, MT_HAND_STEP_FRACTION);
            check_intent(&got, v, 6);
            previous = got;
            have_previous = true;
            ++rows;
        } else if (!strcmp(v[0], "arm")) {
            MtMatPoint landing = { (float)atof(v[1]), (float)atof(v[2]) };
            MtFanHome fresh = { { 0.22f, 0.28f }, false };
            MtFanHome seen = mt_fan_home_seeing(fresh, landing, &layout);
            check_i("latch armed", seen.departed ? 1 : 0, atoll(v[4]));
            ++rows;
        }
    }
    fclose(f);
    printf("  droptargets      %4d points\n", rows);
    return rows;
}

int main(void) {
    printf("mt conformance: the C port against :core's golden vectors\n");
    test_board_solve();
    test_board_slots();
    test_board_slot_at();
    test_set_position();
    test_spring();
    test_ydk();
    test_random();
    test_playfield();
    test_handfan();
    test_droptargets();

    printf("%d checks, %d failures\n", g_checks, g_failures);
    if (g_failures > 25) fprintf(stderr, "(%d further failures not shown)\n", g_failures - 25);
    return g_failures == 0 ? 0 : 1;
}
