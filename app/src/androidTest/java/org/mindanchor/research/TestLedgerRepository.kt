package org.mindanchor.research

import android.content.Context
import org.mindanchor.data.db.AnchorDatabase

/**
 * A [ResearchLedgerRepository] over [database] with a fixed provenance
 * vector.
 *
 * Journal and morning-measure tests need one because those writes now open
 * a study phase, and giving them a real repository rather than a stub is
 * deliberate: it means those suites exercise the same attribution path
 * production takes, so a regression in phase opening fails there too.
 */
internal fun testLedgerRepository(
    context: Context,
    database: AnchorDatabase,
    sourceDeviceId: String = "device-a",
): ResearchLedgerRepository = ResearchLedgerRepository(
    context = context,
    database = database,
    currentVector = {
        ProvenanceVersions.vector(
            appVersionCode = 1,
            appVersionName = "test",
            sourceDeviceId = sourceDeviceId,
        )
    },
)
