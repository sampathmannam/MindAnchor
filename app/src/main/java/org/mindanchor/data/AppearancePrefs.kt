package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.ui.NatureScene

private val Context.dataStore by preferencesDataStore(name = "appearance")

/** Home-screen appearance. Local only, like everything else. */
class AppearancePrefs(private val context: Context) {

    private val sceneKey = stringPreferencesKey("nature_scene")

    /** Defaults to a scene that changes daily. */
    val scene: Flow<NatureScene> = context.dataStore.data.map { prefs ->
        NatureScene.fromKey(prefs[sceneKey])
    }

    suspend fun setScene(scene: NatureScene) {
        context.dataStore.edit { it[sceneKey] = scene.name }
    }
}
