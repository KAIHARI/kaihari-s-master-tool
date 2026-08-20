#include "mt_spring.h"

#include <math.h>

MtSpringSpec mt_spring_critical(float stiffness) {
    MtSpringSpec s;
    s.stiffness = stiffness;
    s.damping = 2.0f * sqrtf(stiffness);
    return s;
}

MtSpringSpec mt_spring_snappy(void) { return mt_spring_critical(520.0f); }

MtSpringSpec mt_spring_bouncy(void) {
    MtSpringSpec s;
    s.stiffness = 380.0f;
    s.damping = 0.62f * 2.0f * sqrtf(380.0f);
    return s;
}

MtSpringSpec mt_spring_calm(void) { return mt_spring_critical(120.0f); }

MtSpringValue mt_spring_step(MtSpringValue state,
                             float target,
                             MtSpringSpec spec,
                             float dt) {
    if (dt <= 0.0f) return state;
    float step = (dt > MT_SPRING_MAX_STEP) ? MT_SPRING_MAX_STEP : dt;

    /* Semi-implicit Euler: the velocity is advanced first and the position is
     * advanced with the *new* velocity. Explicit Euler here injects energy and
     * a stiff spring blows up; this one is stable at every step it is allowed. */
    float force = -spec.stiffness * (state.value - target)
                  - spec.damping * state.velocity;
    float velocity = state.velocity + force * step;

    MtSpringValue out;
    out.velocity = velocity;
    out.value = state.value + velocity * step;
    return out;
}

bool mt_spring_settled(MtSpringValue state,
                       float target,
                       float position_tolerance,
                       float velocity_tolerance) {
    return fabsf(state.value - target) <= position_tolerance
        && fabsf(state.velocity) <= velocity_tolerance;
}
