package org.mindanchor.sim.personas

/**
 * The five personas the launcher is being tested against.
 *
 * The library is a single object so the simulation runner (WP-4)
 * and the data generator (WP-3) can iterate over it without
 * scattering the list. Add a new persona here and the
 * downstream tools pick it up automatically.
 *
 * Citation anchoring is the same list as in
 * `docs/research/22-research-index.md` — if you change a citation
 * in one place, change it in the other. Every persona in this
 * library has a verified citation basis; the build fails (via
 * the unit tests) if any persona is anchored in unverified work.
 */
object PersonaLibrary {

    /** All five personas, in the order the simulation runs them. */
    val all: List<Persona> = listOf(
        MorningLarkPersona(),
        NightOwlPersona(),
        ShiftWorkerPersona(),
        InsomniacPersona(),
        DepressionLowMotivationPersona(),
    )

    /** Look up a persona by its [Persona.id]. */
    fun byId(id: String): Persona? = all.firstOrNull { it.id == id }
}
