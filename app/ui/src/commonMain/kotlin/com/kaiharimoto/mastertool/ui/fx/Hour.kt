package com.kaiharimoto.mastertool.ui.fx

/**
 * What hour it is where the user is, 0..23.
 *
 * A platform seam rather than a dependency, and that is the whole reason it is
 * three lines in three files. `core` deliberately has no platform source sets —
 * it compiles and its tests run anywhere, including environments with no
 * Android SDK — so the one impure question the desk scene asks is asked here,
 * where every other platform question in this app is asked, and `DeskClock`
 * stays a pure function of a setting and a number that a test can supply.
 */
expect fun localHour(): Int
