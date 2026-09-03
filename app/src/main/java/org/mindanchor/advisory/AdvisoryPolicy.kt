package org.mindanchor.advisory

/**
 * Program 3 Task 1 — the advisory rule set's own version.
 *
 * It is declared here, beside the policy that will implement it in Task
 * 3, for the same reason the passive estimator declares its own: a
 * provenance vector must read each component from whatever owns it, so
 * that changing the rules cannot leave the recorded version behind.
 */
object AdvisoryPolicy {
    const val RULE_VERSION = "advisory-opportunity-v1"
}
