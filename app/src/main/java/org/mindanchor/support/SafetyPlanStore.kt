package org.mindanchor.support

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.data.db.SafetyDao
import org.mindanchor.data.db.SafetyPlan

internal interface SafetyPlanStore {
    val plans: Flow<SafetyPlan>
    suspend fun save(command: SaveSafetyPlan): SafetyPlanSaveResult
}

internal data class SaveSafetyPlan(
    val operationId: Long,
    val draft: SafetyPlan,
)

internal sealed interface SafetyPlanSaveResult {
    data class Committed(
        val operationId: Long,
        val stored: SafetyPlan,
    ) : SafetyPlanSaveResult

    data class Failed(
        val operationId: Long,
        val cause: Throwable,
    ) : SafetyPlanSaveResult
}

internal class RoomSafetyPlanStore(
    private val dao: SafetyDao,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : SafetyPlanStore {
    override val plans: Flow<SafetyPlan> = dao.plan().map { it ?: SafetyPlan() }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun save(command: SaveSafetyPlan): SafetyPlanSaveResult = try {
        SafetyPlanSaveResult.Committed(
            operationId = command.operationId,
            stored = dao.savePlanTransaction(command.draft, clockMillis()),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (cause: Throwable) {
        SafetyPlanSaveResult.Failed(command.operationId, cause)
    }
}
