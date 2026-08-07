# A hundred changes toward a game

The play stage has real geometry now — a card is a solid with a normal, the
light is a rig, the shadow is cast by projecting corners rather than drawn as an
offset copy. It is still a diagram that animates rather than a table that
behaves.

This is a hundred things between here and there, grouped by what they touch.
Four of them are marked **[foundation]**: work nothing else can be built on top
of until it exists. Four are marked **[guardrail]**: rules in `docs/DESIGN.md`
that a list this long will try to break, restated so they survive it. Four are
marked **[your call]**: places this argues with something already locked in, or
asks a question only kai can answer.

Nothing here is scheduled. It is a menu.


## 1. The camera

A movable camera is one change in core and eleven consequences. Everything here waits on the first item.

1. **Turn the stage into a camera.** **[foundation]** Yaw, pitch, distance and a target, instead of one hard-coded tilt — keeping `project`, `unproject` and `flatten` exactly as they are, so nothing downstream notices it moved. Every other item in this section waits on this one.

2. **Clamp pitch, lock roll.** Five degrees to seventy-five, and no roll ever. A table you can get underneath is a bug report; a horizon that tips is nausea.

3. **Three seats, not free flight.** Overhead for reading the board, the player's seat for playing, the far side for checking what you are showing. Free-fly cameras always end up somewhere useless and then you have to fly back.

4. **Move the camera on the springs the cards use.** `PosePhysics` is already there. A camera that eases on a curve while the cards spring reads as two physical worlds sharing a screen.

5. **Orbit needs a gesture that isn't already taken.** **[your call]** All four two-finger channels are spoken for on the mat — tap flips, twist turns, drag takes the pile, hold opens the menu. Three fingers, a margin outside the felt, or a held modifier: this needs a decision before it needs code.

6. **Pinch dollies, it does not zoom.** Moving the camera changes the perspective. Scaling the image does not, and that difference is the entire reason to have a camera.

7. **Inertial orbit.** Flick it and it coasts to rest on the same damping. This is most of what makes a camera feel like it weighs something.

8. **Let the tablet's own tilt move it a degree or two.** The cheapest three-dimensional tell that exists on a handheld, and it costs one sensor listener and a low-pass filter.

9. **A CameraFit, solved in core.** The mat has to stay fully on screen at every legal angle — solved once, the way `DeckFit` solves the panes, and never negotiated inside a composable.

10. **Dolly out as a peeked card comes in.** The card grows, the room recedes. It is two lines against the existing peek and it is the most cinematic thing the table could do.

11. **The camera leans toward the action.** A card carried to the far edge pulls the view two or three degrees. Not a follow-cam — a shift of weight.

12. **Remember the angle per deck.** You come back to the seat you left. Preferences are one JSON document; this is a field with a default.


## 2. Light and shading

The renderer exists now. These are the twelve things it does not yet do that separate a lit room from a lit equation.

The three rooms all run on one rig, made a value in `StageLighting`. Read the note on `NIGHT_FLOOR` before touching an ambient: a night scene cannot buy darkness by dimming the room, because card art is shaded by a single black overlay whose error grows sharply as the light falls (`Tone.veil`). Items 16 and 17 are what the desk lamp is actually waiting for.

13. **Add a rim light.** Key and fill both come from in front, so on a true-black table a card's silhouette dissolves into the background. A back light is the thing that cuts it out.

14. **Give the key a temperature.** Warm key, cool fill. Two hex values, and it is most of the difference between a room and an arithmetic result.

15. **Shade in linear space.** The multiply happens in sRGB today, so everything darkens too fast and the midtones go muddy. Convert up, shade, convert back.

16. **Make the key a point light.** Distance attenuation means the far corner of the mat is genuinely dimmer than the near one — which, in a room with a lamp in it, it is.

17. **Give the light a size.** Real soft shadows come from an area source. Offsetting the cast by the light's radius is closer to correct than growing one polygon outward, and costs the same.

18. **Shadows are not black.** A shadow on lit felt is the felt, darker and a little cooler. Neutral black is what a shadow looks like when nobody chose a colour for it.

19. **Occlude between cards.** A card overlapping another should darken the overlap. Every card currently shadows only the felt, so two overlapping cards read as two stickers.

20. **Bloom the specular.** Threshold, blur, add. Without a shader that is a second additive pass at half resolution, which is enough to sell it.

21. **Stretch the foil highlight.** Holofoil is a diffraction grating, so its highlight is a streak along one axis rather than a circular pool. This is the single change that would make foils read as foil.

22. **Draw a card's own edge in the air.** `CardSolid` already computes the slab for every card and only piles render it. A card banked in your hand should be showing its white edge.

23. **A faint reflection of the table in glossy stock.** A darkened, blurred copy of what is below, at grazing angles only, and gone by the time the card is square to you.

24. **Let the light move on big moments.** A summon swings the key across the board. Nothing else in the app ever moves the light, which is exactly what would make it land.


## 3. The card as an object

A card is not a rectangle with a picture on it. Ten ways it could stop being one.

25. **Read rarity from the card database.** YGOPRODeck ships `card_sets` with rarities in it. A secret rare that looks like a common is throwing away data the app already downloaded — and it makes the finish *meaning*, which is the only licence the palette gives colour.

26. **Sleeves as a material, not a texture.** A sleeve is frosted on the back, glossy on the front, and holds the card a millimetre inside itself. All three are visible, and all three are why a sleeved card looks different from a bare one.

27. **Sleeved cards are thicker.** A sleeved deck stands visibly taller. `THICKNESS_RATIO` stops being a constant and becomes a property of the stock.

28. **The back's foil oval is its own layer.** It catches the light separately from the brown field, which is precisely what you see when you fan a deck under a lamp.

29. **Counters become objects.** They are a text badge today. Make them discs that sit on the card, cast their own small shadows, and can be knocked off it.

30. **Dice and coins, simulated.** Both are real game actions rather than decoration, and a tumbling die is the best showcase a rigid-body solver will ever get.

31. **Life points as a thing on the table.** A pad or a dial beside the mat, rather than a number in a top bar. The top bar is the last part of this screen that is still a website.

32. **Let people load their own playmat.** A playmat is the most personal object a player owns and the app currently draws a gradient where it goes.

33. **Edge wear on played cards.** **[your call]** Corner whitening after enough handling. Probably a step too far into skeuomorphism for a tool whose identity is Swiss — noted because somebody will ask for it.

34. **Render card faces once, to a texture.** Compositing an image, a name and three overlays every frame for sixty cards is the cost that will stop everything else on this list from fitting in the budget.


## 4. Physics

Springs to a target are a good animation model and a bad simulation. This is the largest single piece of work here, and about a third of the list depends on it.

35. **A rigid-body solver in core.** **[foundation]** Mass, an inertia tensor, contacts, friction. Tested in `commonTest` like everything else, and it is what turns the play stage from a diagram that animates into a table that behaves.

36. **Cards collide with each other.** Push one card into another and it nudges. This is the biggest single tell of a real table and the app has never had it at all.

37. **Fixed timestep, accumulated.** Otherwise the simulation behaves one way on a 120 Hz tablet and another on a 60 Hz desktop, and every bug report is unreproducible.

38. **Seed everything.** There are two hundred levels of undo in this app. A shuffle that cannot be replayed exactly is a shuffle that breaks undo, and that is not a trade worth making.

39. **Felt has friction, and so do sleeves.** Sleeve-on-sleeve slides differently from card-on-felt, and differently again from card-on-card. Players have strong opinions about this in real life.

40. **Throw a card and it slides.** Released with velocity, decelerating under friction and angular drag, stopping where it stops rather than where a target said.

41. **Dropped cards flutter.** A flat plate falling generates lift, which is why a dropped card never lands where you aimed it. It is simulable, it is cheap, and it is instantly recognisable.

42. **A stack is bodies, not an integer.** `PlacedCard.depth` becomes N cards resting on each other, which is what lets you knock the top one off by accident.

43. **Piles scatter when you hit them.** And the rest of the pile settles into the gap that opened, which is the part that sells it.

44. **Sweep with your hand and everything moves.** Momentum transfer along the whole path of the finger, not just under the point of it.

45. **The riffle is an actual riffle.** Two halves, bridged, interleaved one card at a time — and the order that falls out is the order the deck is now in. The shuffle stops being a permutation with a sound over it.

46. **Cutting the deck.** Take the top half, put it underneath. Already on the roadmap; make it a physical grab rather than a menu item.

47. **The graveyard grows crooked.** Real discard piles are never square. A perfectly aligned graveyard is the clearest possible statement that nothing here is a real object.

48. **A gesture that squares a pile up.** And you watch it happen, because watching it happen is half the pleasure of doing it.


## 5. Animation

Motion explains a change and never announces one. That rule stays; these are the changes that currently go unexplained.

49. **The opening hand is dealt, not placed.** Five cards flicked off the deck in turn, arcing, landing, settling. It is the first thing anybody sees and it currently springs into existence.

50. **Draw from the top of the deck.** The deck has a real height now. The card should leave from up there, and the pile should drop by one card's thickness as it goes.

51. **Flip about the thumb, not the centre.** A real card turns over about the edge you are holding. Nobody can name this and everybody notices it.

52. **Nothing ever lands square.** A degree of rotation and a pixel of offset on every landing. Perfect alignment is the tell, every time.

53. **Anticipation on the pickup.** The card presses into the felt for two frames before it lifts. Two frames.

54. **A sequence type in core.** **[foundation]** Multi-card moments — the deal, a search, a summon — need to be data with a timeline, not nested delays inside a composable. Without this every one of them is written twice and behaves differently.

55. **Every sequence stays interruptible.** The springs already retarget mid-flight. A keyframed sequence that cannot is a sequence that will one day lock the table while a player is waiting to act.

56. **Fan the deck to search it.** Spread, pick, square it back up. The physical answer to a search effect, and the reason to have built the fan.

57. **Banishing is a flick.** The card leaves the table rather than teleporting into a pile. Banish is supposed to feel worse than the graveyard.

58. **Phases get a beat.** A shift in the light and a small camera move, instead of a label changing in a bar.

59. **The turn ends and the board settles.** One quiet moment where everything squares itself and the room takes a breath.

60. **Nothing idles.** **[guardrail]** Breathing cards and drifting light are what an engine does when it has nothing to say. `DESIGN.md` already bans animating something that did not change — restated here because a list this long will try to break it.


## 6. The table and the room

Nine things beyond the felt. A table floating in a void is the most common way a three-dimensional scene looks cheap.

61. **The table has an edge** — *three of the four are there.* The desk runs off both sides of the picture, and because `BoardLayouter` centres a seven-column field, a third of the width down each side is bare wood, plus a strip above the mat before the wall. That is where the felt stops and the wood starts, at every seat. The **near** edge is not reachable: the board fills the stage vertically, so the desk's near edge projects below the glass at every seat and dollying out does not bring it back (the term that puts it there is `flat`, which distance does not scale). It would take a smaller board or a wider envelope. See `Scenery.DESK_NEAR`.

62. ~~**There is a room past it.**~~ **Done, at the size this asked for.** One wall behind the desk, dark, and nothing else. The sentence was the specification and it is worth keeping: *dark, out of focus, present; it does not need detail, it needs to exist.*

63. **The opponent's half exists.** A duel mat has two sides and the geometry reads as wrong without the other one, even empty, even unplayable.

64. **The zones are printed, not stroked.** Ink on the mat, catching the key at a grazing angle, rather than two hairlines drawn on a canvas.

65. **The felt has a weave.** Procedural and sub-pixel. It is what stops a large flat surface from reading as a fill colour.

66. **Dust, and a crease or two.** One well-chosen imperfection is worth ten polish passes on everything else.

67. ~~**A choice of surface.**~~ **Done.** `Scene` is a preference — minimal or the desk — and `DeskLight` picks the hour or leaves it to the clock. What is *not* done is the material half of it: every surface is still one Blinn-Phong response, so a rubber mat and a bare table differ in colour rather than in finish.

68. **Depth of field from the low seat.** Sharp from overhead, soft at the far edge from the player's chair. The per-card blur radius comes straight off `Projected.depth`, which is already computed.

69. **A photo mode.** Freeze it, free the camera, hide the chrome, export a PNG. Board states are the thing players actually share with each other.


## 7. Sound

Short, quiet, and only for things a hand does. Eight ways to keep that rule and still make the table louder.

70. **Pan to where it happened.** The projected x of every event is already computed sixty times a second.

71. **Loudness from impact speed.** A card slapped down and a card placed play the identical sample today, at the identical level.

72. **Three variants of everything.** Repeating one sample is exactly how a table starts sounding like a machine, and it takes about four repeats.

73. **Sleeve, felt, card — three surfaces.** The material system already knows which two are meeting. It just is not being asked.

74. **The riffle sound comes from the riffle.** One tick per interleave, so the rhythm is different every shuffle, because the shuffle is.

75. **A pile tapped square.** The most satisfying sound in the game, and nothing in this app makes it.

76. **A mix bus with ducking.** Four card sounds inside one second currently stack into noise instead of into a hand doing something quickly.

77. **Still no room tone.** **[guardrail]** Ambience is the standing temptation of every project like this one, and the handbook already refuses it. Sound is for things a hand does. Listed so it stays refused.


## 8. Haptics

The vocabulary shipped this week. Seven ways it stops being a lookup table and starts being an output of the simulation.

78. **Amplitude from the simulation.** Impact velocity drives strength, instead of one constant frozen into each event at authoring time.

79. **Texture during a drag.** A card crossing another card's edge should be felt going over it. Primitives cannot do this; a very low-amplitude waveform can.

80. **The riffle is felt one card at a time.** Same source as the sound, so the two cannot drift apart into a buzz and a noise that disagree.

81. **Weight.** A forty-card deck lifts differently from a single card, and amplitude is the only channel that can say so.

82. **Detents on the camera seats.** The twist already has them, and they are the reason that gesture works without looking at it.

83. **A refusal.** Two dull thuds for a move the table will not make. It is the one thing the vocabulary is currently missing.

84. **Everything stays under sixty milliseconds.** **[guardrail]** The rule that already governs the set. Restated because every new event will want to be the exception, and the riffle already is.


## 9. Spectacle

Where the budget goes. Seven moments — and the discipline that keeps them moments rather than a permanent condition.

85. **A summon is an event.** The one place to spend everything at once: light, camera, a held frame, silence before it. If this whole list only ever buys one moment, buy this one.

86. **Effects resolve in three beats.** Activation, the window to respond, resolution. Quiet beats — the pacing is the thing, not the effect.

87. **Attacks.** Two cards, one line between them, and enough geometry that it reads from any camera seat rather than only from overhead.

88. **Damage is felt.** The counter, the sound and the haptic together, all three scaled by how much. Life points are the only number in the game with stakes.

89. **The prismatic ring stays rare.** **[guardrail]** It means *this is the one you asked for* and it is reserved for reveals. A summon that fringes everything spends the whole visual identity in a single turn.

90. **Slow motion, once.** For the last card of a combo, and never twice in a row. The second time is a cutscene.

91. **A reset that feels like clearing a table.** Sweep it all into a pile, square it, deal again. Right now a new hand is a state swap with a fade.


## 10. Foundations

The unglamorous nine. None of them are visible, and about seventy of the items above quietly assume all of them.

92. **A retained scene in core.** **[foundation]** A list of renderables with transforms, sorted once. The room is the first thing to run into this: nothing in a `SceneModel` may stand over the mat, because cards are sorted in the composable tree and the room is painted beneath all of them, and a prop that needed to be in front of a card could not be. That restriction lifts here and nowhere else. The composable tree *being* the scene is what will stop this scaling somewhere north of eighty objects, and the play stage already holds sixty.

93. **A real depth sort.** The plane-then-air invariant works because resting cards are flat. Give everything a height and it stops working — quietly, and only on boards with a lot on them.

94. **Batch by material.** One path per surface type rather than one per card. The shadow pass alone currently allocates a few hundred paths a frame.

95. **Golden-image tests.** Render to a bitmap in `commonTest` and compare. A hundred visual changes without this is a hundred silent regressions in each other's work.

96. ~~**A frame budget readout.**~~ **Done.** `FrameProbe` was written and wired to nothing; it is in the play stage's frame loop now, behind three taps on the life-point number. It reports the last frame, p95, the worst in the window, missed frames and how many objects were moving, against a budget derived from the panel's own refresh rate.

97. **Level of detail.** A card at the far edge of the mat does not need a specular pool, a soft shadow or a foil sweep.

98. **A quality setting.** The Tab S11 can take all of this. A five-year-old phone cannot, and the app should not discover that by dropping frames on somebody.

99. **A shader seam behind expect/actual.** **[your call]** Android 13+ has `RuntimeShader`, desktop Skia has `RuntimeEffect`, and neither is common code — but a thin platform seam with a plain-draw fallback is reachable, and it is what unlocks real blur, bloom and foil. This is the one place the no-engine rule deserves re-reading rather than restating.

100. **Decide what this is.** **[your call]** A simulator you play *on* and a game you play *against* want different work. Roughly thirty of the items above only make sense for the second one, and that is a decision rather than a discovery.


## Where it starts

Four items gate most of the rest, in this order:

1. **A rigid-body solver in core** (#35) — a third of the list is downstream of it.
2. **Turn the stage into a camera** (#1) — everything in §1, and it changes what
   every shading term is computed against.
3. **A sequence type in core** (#54) — without it every multi-card moment is
   written twice.
4. **A retained scene in core** (#92) — the ceiling everything else runs into.

And two that are not features at all, and should have come before any of them.
Both now exist: **golden-image tests** (#95) as `GoldenStageTest`, and **a
frame budget readout** (#96) in the play stage's loop. A hundred visual changes
without those two would have been a hundred silent regressions in each other's
work — the first of them, making the rig a value, was proved harmless by the
golden going green without being re-recorded.
