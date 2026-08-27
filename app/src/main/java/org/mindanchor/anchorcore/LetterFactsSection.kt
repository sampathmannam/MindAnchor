package org.mindanchor.anchorcore

/**
 * Renders AnchorState into the letter-prompt block (Hook A). Bullets
 * only: the model reads sentences, the person reads the letter, and
 * neither is served by adjectives. Direction-only wording comes from
 * DayFactRenderer; this object just frames the lines as observations
 * of the person's own data.
 *
 * @wording-reviewed — the section header line reaches the model as
 * context for user-visible prose; same review discipline as the
 * renderers it wraps.
 */
object LetterFactsSection {

    fun compose(state: AnchorState): String? {
        val steady = state as? AnchorState.Steady ?: return null
        if (steady.facts.isEmpty()) return null
        return steady.facts.joinToString("\n") { "- ${DayFactRenderer.render(it.kind, it.detail)}" }
    }
}
