/*
 * A damped spring, the unit of card motion - the C form of
 * `core/motion/Spring.kt`.
 *
 * Everything a card does reads as having mass because it is a spring
 * approaching a target: it arrives with the deceleration of a real thing, and
 * interrupting it mid-flight composes naturally, because the new target simply
 * takes over with the velocity intact.
 *
 * On the 3DS it does that job and a second one. The marker tracker in `src/ar/`
 * produces a pose per CV frame, at 15-30Hz, against a screen running at 60 -
 * and a pose that jumped on the frames a measurement landed on would read as a
 * table twitching on a desk. Filtering it is the same problem as landing a card:
 * a target that moves, a state with momentum, and a need to look like an object
 * rather than a sequence of samples. So the pose is smoothed by these, and no
 * second filter is written.
 */
#ifndef MT_SPRING_H
#define MT_SPRING_H

#include <stdbool.h>

typedef struct {
    float stiffness;
    float damping;
} MtSpringSpec;

/** One animated scalar: where it is and how fast it is going. */
typedef struct {
    float value;
    float velocity;
} MtSpringValue;

/** Mass is 1, so critical damping is 2*sqrt(stiffness). */
MtSpringSpec mt_spring_critical(float stiffness);

/** Settles fast with no overshoot - snaps, docks, returns. */
MtSpringSpec mt_spring_snappy(void);
/** A touch of overshoot - lifts and lands that feel like mass. */
MtSpringSpec mt_spring_bouncy(void);
/** Slow and weighty - big scene moves, camera-like drifts. */
MtSpringSpec mt_spring_calm(void);

/**
 * Clamped, and the clamp is the point.
 *
 * A frame the app was not scheduled for - the HOME menu opening, a wi-fi
 * download landing, an SD read stalling - arrives as a large dt, and a large dt
 * in a semi-implicit Euler step is a card teleporting across the table or, with
 * a stiff enough spring, diverging outright. One thirtieth of a second is the
 * longest step anything here is allowed to take, however long the frame was.
 */
#define MT_SPRING_MAX_STEP (1.0f / 30.0f)

MtSpringValue mt_spring_step(MtSpringValue state,
                             float target,
                             MtSpringSpec spec,
                             float dt);

/** Close enough to stop integrating and snap to the target. */
bool mt_spring_settled(MtSpringValue state,
                       float target,
                       float position_tolerance,
                       float velocity_tolerance);

#endif /* MT_SPRING_H */
