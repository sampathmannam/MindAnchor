package org.mindanchor.sim

import org.junit.Test
import org.mindanchor.sim.personas.PersonaLibrary
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate

/**
 * Diagnostic dump — print the simulation output for inspection.
 *
 * Disabled in the normal test run; invoke directly when investigating
 * a launcher's behaviour on a specific persona. Output is on stdout.
 */
class PersonaSimulationDump {

    @Test
    fun `dump morning lark simulation`() {
        val persona = PersonaLibrary.byId("morning_lark_healthy")!!
        val start = LocalDate.of(2026, 1, 5)
        val days = WellnessSimulationRunner.run(persona, start, seed = 42L)
        println("=== Morning Lark ===")
        for (day in days) {
            val hrv = day.readings[WellnessSignal.HRV]!!
            val baseline = hrv.baseline
            println(
                "${day.date}  " +
                    "hrv=${hrv.today}  " +
                    "baseline.median=${baseline.median}  " +
                    "baseline.mad=${baseline.mad}  " +
                    "sampleCount=${baseline.sampleCount}  " +
                    "zScore=${hrv.zScore}  " +
                    "direction=${hrv.direction}",
            )
        }
    }

    @Test
    fun `dump insomniac simulation`() {
        val persona = PersonaLibrary.byId("insomnia_anxious")!!
        val start = LocalDate.of(2026, 1, 5)
        val days = WellnessSimulationRunner.run(persona, start, seed = 42L)
        println("=== Insomniac ===")
        for (day in days) {
            val hrv = day.readings[WellnessSignal.HRV]!!
            val baseline = hrv.baseline
            println(
                "${day.date}  " +
                    "hrv=${hrv.today}  " +
                    "baseline.median=${baseline.median}  " +
                    "baseline.mad=${baseline.mad}  " +
                    "sampleCount=${baseline.sampleCount}  " +
                    "zScore=${hrv.zScore}  " +
                    "direction=${hrv.direction}",
            )
        }
    }
}
