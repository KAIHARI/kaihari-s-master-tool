# What DuelingBook knows, and what to do about it

Research notes and a design, from reading how the interface most Yu-Gi-Oh!
players actually use is built — and where this app already beats it, where it
loses badly, and what the difference says about what to build next.

## 1. What DuelingBook proves

**Manual is the right choice, and it is not a compromise.** DuelingBook does not
resolve card effects. You press draw to draw. You press attack and pick a
target. Every simulator that automates effects (Master Duel, EDOPro) can only
ever play the cards someone has scripted, which is why the competitive practice
scene lives on the manual one. This app's play stage made the same call — "the
rules of the game living entirely in your head, which is what a table is" — and
the research confirms it is the majority position rather than a shortcut.

**The hot path is searching your deck, and typing beats browsing.** The single
most frequent non-trivial action in modern Yu-Gi-Oh is fetching a specific card
out of a sixty-card deck, and DuelingBook's native flow for it — open the deck
viewer, scroll, click, menu, "add to hand" — is slow enough that the community
built a command layer on top of it. `/search <name>` adds a card from deck to
hand. `/dig <name>` views the deck filtered by name. Then `/send` to mill it,
`/ban` to banish it, `/atk` and `/def` to summon it, `/st` to set it, `/ex <n>`
to excavate. Most of those are a *third-party extension*, which is the tell: the
native interface was slow enough that people wrote their own.

**Its own users say the menu is the problem.** One extension exists to add
"fully customizable hotkeys" across eighteen-plus commands specifically to
eliminate "repetitive clicking through menus"; another advertises letting you
"left-click a card to interact with it in many ways without needing the janky
menu", and unlocks thirty-plus card actions the base client does not expose at
all. A long drawer of options attached to every card is the thing DuelingBook is
most criticised for.

**And the competing manual mode wins on exactly that axis.** YGO Omega's manual
mode is described as easier "because you just need to point and click to the
location where you want to move a card to rather than select from a long drawer
of options."

That last sentence is worth reading twice, because it is a description of a
design decision this app already made.

## 2. Where this app already wins

- **Movement.** `DropTargets` resolves the finger's position to one intent, the
  highlight draws that value, and the release commits the same value. There is
  no drawer. This is the thing Omega is praised for and DuelingBook is
  criticised for, and it is already the deepest rule in `docs/DESIGN.md` §10.
- **Undo.** Two hundred levels, over an immutable field. DuelingBook's is far
  weaker, and for combo practice — where the whole activity is "try the line
  again from three steps back" — undo is not a convenience, it is the feature.
- **The deck builder.** Lenses, exact opening rates, goals stored with the deck.
  Nothing on DuelingBook is close.
- **Feel.** Nothing else in this space has a card with a thickness, a light and
  a cast shadow, or a haptic vocabulary.

## 3. The hole

**Nothing in this app can ask for a card that is not on top of a pile.**

`PlayField` already exposes `playFromDeck(index, at, position)`, and the same
for the extra deck, the graveyard and the banished pile. The domain has been
able to play an arbitrary card out of any pile since it was written. But
`MatInput.whatIsUnder` returns `DragOrigin.Pile(slot, 0)` — always index zero —
so no gesture in the app can name anything but the top card.

The consequences are worse than "search is missing":

- You cannot **search your deck**, the most common action in the game.
- You cannot **choose an extra deck monster**. Every Xyz, Synchro, Fusion and
  Link play is unreachable, which is most of what combo practice *is*.
- You cannot **read your own graveyard** — you can peek its top card and nothing
  else — which decides whether half the deck's effects are live.
- You cannot **look at the banished pile**, same problem.

This is not a domain change. It is a way of asking. That is the whole of the
work, and everything below is downstream of it.

## 4. The fan

One gesture, one surface, and it reuses everything.

**Hold a pile and it fans out.** The one-finger hold already means "let me look
at this" — it is the peek. On a *pile* it spreads the pile into a row across the
near half of the table, above the hand and in front of the board. The board
stays visible and dimmed rather than covered, because §8 of the handbook is
explicit: anything requiring you to look at the deck while you do it must not be
a sheet.

This also resolves an existing awkwardness. The peek is currently refused on the
deck, on the grounds that the top of your own deck is the one card a goldfish is
only honest without. That reasoning does not apply to a fan: fanning your deck
is a *search*, which is a legal, public, everyday action. The deck stops being
the one pile you cannot interact with.

**Order says what the pile is.** The graveyard and the banished pile fan in
recency order, because that order is real and it matters — which card was sent
first is a fact about the game. The deck does **not** fan in deck order, because
deck order is secret and showing it would leak the shuffle. It fans in the order
of the **current lens**: the user's own roles, or archetype, or type, or copy
count. The deck builder already computes that partition (`core/deck/DeckLens.kt`),
and searching your deck through the same lens you built it with is something no
other simulator can do, because no other simulator knows what a starter is.

**Narrowing is tapping, not typing.** DuelingBook's command layer proves typing
beats browsing on a keyboard — so on desktop, type and the fan narrows. But this
app is for a tablet, and a search that demands an on-screen keyboard has already
lost. So the lens keys become filter chips along the fan: tap *Starters* and the
fan narrows to your starters. Tap *Extenders*. That is the same reduction as
typing four letters, with no keyboard and one touch, and it is only possible
because the deck already carries the labels.

**Taking a card is a drag, not a menu.** You drag a card straight out of the fan
onto the mat, and every existing rule applies unchanged — `DropTargets` resolves
it, the indicator shows it, `DropCommit` carries it out. Searching to hand,
milling to the graveyard, banishing from deck, summoning out of the extra deck
and setting from the deck are then *the same gesture aimed somewhere different*,
rather than six commands to remember. `/search`, `/send`, `/ban`, `/atk`, `/def`
and `/st` all collapse into one motion.

**Closing the deck fan shuffles the deck.** Because you just searched it. It is
correct by the rules, it costs nothing, and it stops the tool teaching a habit
that would lose a game. The graveyard and banished fans do not shuffle, because
those piles are ordered and public.

## 5. Everything else, mapped

Ordered by what it would change about a practice session, not by effort.

1. **Tokens.** A real functional gap — a large share of modern combos put tokens
   on the board and they are currently unrepresentable. Conjure one onto the mat;
   it behaves as a card with no back.
2. **A scrubbable history.** The undo stack is already two hundred immutable
   fields deep, which means the app is one slider away from letting you scrub
   backwards and forwards through a combo the way you scrub a video. DuelingBook
   has replays of finished duels; nobody has this *while you are practising*, and
   it is close to free.
3. **Card text on the peek.** A held card magnifies its art and shows nothing to
   read. In practice you hold a card up precisely to check the wording. Show the
   text beside it.
4. **Excavate.** Turn the top N of the deck face-up in a row without committing
   to any of them — the `/ex` command, as a drag off the deck.
5. **Mill by number.** `/send`, as a drag from the deck to the graveyard with a
   count.
6. **A real life-point pad.** The top bar has four fixed buttons; DuelingBook has
   a calculator with operators because damage is arithmetic. Make it a pad, and
   put it on the table rather than in a bar.
7. **Dice and a coin.** Both are real game actions, not decoration, and both are
   the best possible showcase for the rigid-body solver in `docs/AAA.md`.
8. **Activation as a state.** A card being activated lifts and stays lifted until
   it resolves. In a long combo, what is currently on the chain is a thing you
   have to remember; the table could hold it for you without knowing any rules.
9. **Attach as material by gesture.** `DropIntent.Attach` exists in the domain
   and has no idiom. Once the fan exists, the extra deck is reachable and Xyz
   play becomes the common case rather than an edge case.
10. **The card menu is our own janky drawer.** `CardActions` is twelve options in
    a Material `DropdownMenu`, which is exactly the pattern DuelingBook is most
    criticised for and exactly the component §8 says to restyle on the way in.
    Most of its entries are destinations, and destinations belong on the table:
    a card lifted and dropped on the graveyard should not need a menu at all.
11. **A pile's count, always visible.** Deck, graveyard and banished all show a
    number in every other simulator. Ours shows a pile height and nothing else.
12. **Search-and-shuffle honesty for the extra deck too** — no shuffle, since the
    extra deck is public information.
13. **A "reveal" that means something in solo.** Turning a card face-up to the
    room is meaningless alone, but *marking* a card — this is the one I am
    holding for the trap — is genuinely useful in practice.
14. **Phase-aware nothing.** DuelingBook's phase buttons do not enforce anything
    either. Keep it that way; the phase is a note to yourself.
15. **Timers.** Competitive players practise against the clock. Low priority,
    trivially cheap, worth remembering.
16. **Favourites.** DuelingBook lets you hold right-click to favourite a card so
    you can find your staples across every deck. Our equivalent already exists
    and is better: the lens. Do not build favourites; make the lens do it.
17. **Hotkeys for the fan.** Every gesture ships with both idioms, so the fan
    needs keys: one per pile, plus type-to-filter on desktop.
18. **Multi-select.** Taking three cards out of the graveyard at once is common
    (banishing for a cost). The mat has no notion of a selection.
19. **Sleeves and a playmat.** DuelingBook's whole cosmetic economy. We have card
    backs already; a user playmat is in `docs/AAA.md` §6.
20. **Spectating, chat, rated duels, replays of others.** All require an
    opponent. Out of scope until this stops being a solo tool, and possibly
    forever — the thing this app is good at is *practice*.

## 6. What not to copy

- **The drawer.** Every destination that can be a place on the table should be a
  place on the table. The menu is for what is left over.
- **Commands as the fast path.** They are a symptom. If the direct manipulation
  is good enough, `/search` never needs to exist — and if it does need to exist,
  the direct manipulation is not good enough yet.
- **Modal viewers.** The reason DuelingBook's deck viewer is slow is not the
  scrolling, it is that it is a window over the game. You choose a card *because
  of* what is on the board.
