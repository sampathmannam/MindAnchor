package org.mindanchor.research

import java.time.LocalDate
import java.util.UUID
import org.mindanchor.data.db.MorningMeasureEntity

/**
 * The five-item morning research measure: one 1-5 rating each for mood,
 * anxiety/tension, anger-or-urge-to-react, energy/ability-to-function, and
 * perceived sleep quality.
 *
 * This is a personal research measure, not a diagnosis or clinical score.
 * It intentionally carries no derived total, threshold, or interpretation
 * anywhere — the raw five values are the entire record.
 */
data class MorningMeasure(
    val id: String,
    val localDate: String,
    val createdAt: Long,
    val updatedAt: Long,
    val mood: Int,
    val anxiety: Int,
    val angerUrge: Int,
    val energyFunction: Int,
    val sleepQuality: Int,
    val instrumentVersion: String,
    val sourceDeviceId: String,
) {
    companion object {
        const val INSTRUMENT_VERSION = "morning-v1"

        /**
         * [id] and [createdAt] default to a fresh UUID and [now] respectively,
         * but can be supplied so a repository can upsert-by-date: reusing the
         * same [id] and the original [createdAt] when a record for the day
         * already exists, while still routing every write (including edits)
         * through this same validation.
         */
        fun create(
            localDate: LocalDate,
            now: Long,
            mood: Int,
            anxiety: Int,
            angerUrge: Int,
            energyFunction: Int,
            sleepQuality: Int,
            sourceDeviceId: String,
            id: String = UUID.randomUUID().toString(),
            createdAt: Long = now,
        ): MorningMeasure {
            requireInRange("mood", mood)
            requireInRange("anxiety", anxiety)
            requireInRange("angerUrge", angerUrge)
            requireInRange("energyFunction", energyFunction)
            requireInRange("sleepQuality", sleepQuality)
            return MorningMeasure(
                id = id,
                localDate = localDate.toString(),
                createdAt = createdAt,
                updatedAt = now,
                mood = mood,
                anxiety = anxiety,
                angerUrge = angerUrge,
                energyFunction = energyFunction,
                sleepQuality = sleepQuality,
                instrumentVersion = INSTRUMENT_VERSION,
                sourceDeviceId = sourceDeviceId,
            )
        }

        private fun requireInRange(name: String, value: Int) {
            require(value in 1..5) { "$name must be between 1 and 5, was $value" }
        }
    }
}

fun MorningMeasure.toEntity(): MorningMeasureEntity = MorningMeasureEntity(
    id = id,
    localDate = localDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    mood = mood,
    anxiety = anxiety,
    angerUrge = angerUrge,
    energyFunction = energyFunction,
    sleepQuality = sleepQuality,
    instrumentVersion = instrumentVersion,
    sourceDeviceId = sourceDeviceId,
)

fun MorningMeasureEntity.toDomain(): MorningMeasure = MorningMeasure(
    id = id,
    localDate = localDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    mood = mood,
    anxiety = anxiety,
    angerUrge = angerUrge,
    energyFunction = energyFunction,
    sleepQuality = sleepQuality,
    instrumentVersion = instrumentVersion,
    sourceDeviceId = sourceDeviceId,
)
