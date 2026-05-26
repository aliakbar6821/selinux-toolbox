package com.selinuxtoolbox.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selinux_toolbox_prefs"
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACTIVE_PROJECT_ID = longPreferencesKey("active_project_id")
        val OUTPUT_FOLDER_PATH = stringPreferencesKey("output_folder_path")
        val LAST_PROJECT_FOLDER = stringPreferencesKey("last_project_folder")
        val DENIAL_CAPTURE_ENABLED = stringPreferencesKey("denial_capture_enabled")
    }

    val activeProjectId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PROJECT_ID]
    }

    val outputFolderPath: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.OUTPUT_FOLDER_PATH] ?: defaultOutputPath()
    }

    suspend fun setActiveProjectId(id: Long?) {
        context.dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(Keys.ACTIVE_PROJECT_ID)
            } else {
                prefs[Keys.ACTIVE_PROJECT_ID] = id
            }
        }
    }

    suspend fun setOutputFolderPath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.OUTPUT_FOLDER_PATH] = path
        }
    }

    suspend fun setLastProjectFolder(path: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_PROJECT_FOLDER] = path
        }
    }

    val lastProjectFolder: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_PROJECT_FOLDER]
    }

    private fun defaultOutputPath(): String =
        android.os.Environment.getExternalStorageDirectory().absolutePath +
                "/SELinuxToolbox"
}
