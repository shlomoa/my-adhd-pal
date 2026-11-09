package com.shlomoa.myadhdpal.phone.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shlomoa.myadhdpal.phone.data.PublishedPrompt
import com.shlomoa.myadhdpal.phone.data.PublishedPrompts
import com.shlomoa.myadhdpal.phone.data.contract.PublishedPromptsContract
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStorePublishedPromptsTest : PublishedPromptsContract() {
    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() = scopes.forEach { it.cancel() }

    private fun open(): PublishedPrompts {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val file = File(folder.root, "prompts.preferences_pb")
        return DataStorePublishedPrompts(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    }

    override fun create(): PublishedPrompts = open()

    @Test
    fun `TC-S-195 the prompts survive a new store on the same file`() = runTest {
        val expiry = Instant.parse("2026-10-17T12:00:00Z")
        open().add("p1", expiry)
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
        scopes.clear()
        assertEquals(listOf(PublishedPrompt("p1", expiry)), open().all())
    }
}
