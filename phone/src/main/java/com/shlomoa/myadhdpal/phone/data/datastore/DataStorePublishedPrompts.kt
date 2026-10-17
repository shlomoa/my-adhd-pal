package com.shlomoa.myadhdpal.phone.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shlomoa.myadhdpal.phone.data.PublishedPrompt
import com.shlomoa.myadhdpal.phone.data.PublishedPrompts
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Keeps the published prompts on Preferences DataStore as `id|expiryMillis` entries (the expiry is empty when there is
 * none). An entry that cannot be read is skipped, so a damaged value never stops the others from being removed.
 */
class DataStorePublishedPrompts(private val store: DataStore<Preferences>) : PublishedPrompts {
    override suspend fun add(id: String, expiresAt: Instant?) {
        store.edit { prefs ->
            prefs[PROMPTS] = prefs.entries().filterNot { it.id == id }.plus(PublishedPrompt(id, expiresAt)).encoded()
        }
    }

    override suspend fun remove(id: String) {
        store.edit { prefs -> prefs[PROMPTS] = prefs.entries().filterNot { it.id == id }.encoded() }
    }

    override suspend fun all(): List<PublishedPrompt> = store.data.first().entries()

    private fun Preferences.entries(): List<PublishedPrompt> = this[PROMPTS].orEmpty().mapNotNull(::decode)

    private fun List<PublishedPrompt>.encoded(): Set<String> =
        map { "${it.id}$SEPARATOR${it.expiresAt?.toEpochMilli() ?: ""}" }.toSet()

    private fun decode(entry: String): PublishedPrompt? {
        val parts = entry.split(SEPARATOR, limit = 2)
        val id = parts[0]
        val millis = parts.getOrNull(1)
        return when {
            id.isEmpty() || millis == null -> null
            millis.isEmpty() -> PublishedPrompt(id, null)
            else -> millis.toLongOrNull()?.let { PublishedPrompt(id, Instant.ofEpochMilli(it)) }
        }
    }

    private companion object {
        val PROMPTS = stringSetPreferencesKey("published_prompts")
        const val SEPARATOR = "|"
    }
}
