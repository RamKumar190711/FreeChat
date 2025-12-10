package com.toqsoft.freechat.coreModel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


private val Context.dataStore by preferencesDataStore("user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_DISCOVERED_USERS = stringPreferencesKey("discovered_users")

    // Username Flow
    val usernameFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[KEY_USERNAME] }

    suspend fun saveUsername(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = name
        }
    }

    // Discovered Users Flow
    val discoveredUsersFlow: Flow<List<String>> = context.dataStore.data
        .map { prefs ->
            prefs[KEY_DISCOVERED_USERS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        }

    suspend fun saveDiscoveredUsers(users: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISCOVERED_USERS] = users.joinToString(",")
        }
    }
}
