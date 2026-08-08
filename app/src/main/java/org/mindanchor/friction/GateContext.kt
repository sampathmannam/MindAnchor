package org.mindanchor.friction

/**
 * Everything the friction gate needs to render a single
 * opening of the [FrictionGate] composable, computed once at
 * the moment of the open-attempt.
 *
 * Holding the reach count, the tone decision, the optional
 * small thing, the optional if-then plan, and the optional
 * compassion moment in a single record keeps the gate's
 * signature stable: one [GateContext] in, one decision out.
 * The pure-function half of every field here is testable
 * without a device; the [LauncherViewModel] is the only
 * thing that talks to the DataStore, the per-user median,
 * and the bandit state.
 *
 * The defaults are all "no extras" — a fresh user with no
 * plans, no small things, no compassion moments sees the
 * existing generic prompt. The defaults are *additive*: they
 * extend the existing behaviour, never modify it.
 *
 * The [banditArm] field records which arm of the bandit was
 * played for this gate event, when one was. Null when the
 * deterministic tone was already BRIEF or FEATHER (the
 * bandit does not intervene for those cases) or when the
 * user has not yet accumulated enough state for the bandit
 * to make a decision. The arm is read once and recorded
 * once, so the outcome (user proceeded or backed out) can
 * update exactly the arm that was played, even if the
 * user lingers on the gate for seconds before deciding.
 */
data class GateContext(
    val tone: FrictionTone,
    val banditArm: FrictionBandit.ArmChoice? = null,
    /** The user's own small thing, or null. */
    val smallThing: String? = null,
    /** The user's pre-written if-then plan for this app, or null. */
    val ifThenPlan: IfThenPlan? = null,
    /** The user's self-compassion moment for this reach, or null. */
    val compassionMoment: String? = null,
)
