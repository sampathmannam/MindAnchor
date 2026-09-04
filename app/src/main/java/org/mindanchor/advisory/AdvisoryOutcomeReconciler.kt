package org.mindanchor.advisory

import android.content.Context
import androidx.room.withTransaction
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.mindanchor.continuity.ContinuityWorkScheduler
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.journal.ChangeOperation
import org.mindanchor.research.ResearchLedgerRepository

/**
 * Program 3 Task 4 — this build's registry of outcome instruments
 * compatible with an advisory protocol, kept deliberately empty.
 *
 * There is no PHQ/GAD/WHO-5-shaped adapter here, on purpose. Adding one
 * would be a claim that a completed episode's effect is measurable, and
 * this design's whole position is that it is not — the honest content
 * of a completed episode is the record of it, not an inferred outcome.
 */
object CompatibleOutcomeInstrumentRegistry {
    // The parameter and the constant return are both intentional: the
    // shape is what a real registry would have once one exists, and the
    // whole content of this object is that it always returns null today.
    @Suppress("FunctionOnlyReturningConstant", "UnusedParameter")
    fun compatibleWith(protocol: ProtocolKey): Nothing? = null
}

/** Closes outcome windows a completed episode opened, once they come due. */
interface AdvisoryOutcomeReconciler {
    suspend fun reconcile(now: Long, zoneId: ZoneId, requestCheckpoint: Boolean = true): Int
}

/**
 * The one [AdvisoryOutcomeReconciler]: for every due, still-open outcome
 * window, append exactly one [EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING]
 * event naming [MissingOutcomeReason.NO_REGISTERED_COMPATIBLE_INSTRUMENT]
 * — because [CompatibleOutcomeInstrumentRegistry] never has anything
 * else to say. This never infers success, failure, or any effect from
 * the episode; the missing reason is the entire content of the closure.
 */
class RoomAdvisoryOutcomeReconciler(
    private val context: Context,
    private val database: AnchorDatabase,
    private val researchLedger: ResearchLedgerRepository,
) : AdvisoryOutcomeReconciler {

    @Suppress("ReturnCount")
    override suspend fun reconcile(now: Long, zoneId: ZoneId, requestCheckpoint: Boolean): Int {
        val closed = database.withTransaction {
            val events = database.advisory().eventsNow()
            val byEpisode = events.groupBy { it.episodeId }
            var appended = 0
            byEpisode.forEach { (episodeId, chain) ->
                if (AdvisoryCodec.verifyEpisodeChain(chain) != EventChainVerdict.VALID) return@forEach
                val sorted = chain.sortedBy { it.sequence }
                val opened = sorted.lastOrNull { it.eventType == EpisodeEventType.OUTCOME_WINDOW_OPENED.name }
                    ?: return@forEach
                val alreadyClosed = sorted.any {
                    it.eventType == EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING.name
                }
                if (alreadyClosed) return@forEach
                val window = AdvisoryCodec.json.decodeFromString<OutcomeWindowOpenedPayloadV1>(opened.payloadJson)
                if (window.closesAt > now) return@forEach

                val closeEvent = AdvisoryCodec.seal(
                    opened.copy(
                        id = "",
                        sequence = opened.sequence + 1,
                        eventType = EpisodeEventType.OUTCOME_WINDOW_CLOSED_MISSING.name,
                        occurredAt = now,
                        payloadJson = AdvisoryCodec.json.encodeToString(
                            MissingOutcomePayloadV1(MissingOutcomeReason.NO_REGISTERED_COMPATIBLE_INSTRUMENT),
                        ),
                        previousEventHash = opened.eventHash,
                        eventHash = "",
                    ),
                )
                if (database.advisory().insertEvents(listOf(closeEvent)).firstOrNull() == IGNORED_ROW_ID) return@forEach

                database.journal().insertChange(
                    ContinuityChangeEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "INTERVENTION_EPISODE_EVENT",
                        entityId = closeEvent.id,
                        operation = ChangeOperation.CREATE.name,
                        occurredAt = now,
                        acknowledgedSnapshotId = null,
                    ),
                )
                appended += 1
            }
            appended
        }
        if (closed > 0) {
            researchLedger.provenance.refreshAfterCommit()
            if (requestCheckpoint) {
                ContinuityWorkScheduler.requestCheckpoint(context)
            }
        }
        return closed
    }

    companion object {
        private const val IGNORED_ROW_ID = -1L

        fun build(context: Context): RoomAdvisoryOutcomeReconciler {
            val app = context.applicationContext
            return RoomAdvisoryOutcomeReconciler(
                context = app,
                database = AnchorDatabase.get(app),
                researchLedger = ResearchLedgerRepository.build(app),
            )
        }
    }
}
