package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Side
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stores what the watch sends and writes the phone's own hello once at start. For each event the history row is stored
 * first and only then is the event marked stored, which makes the source delete the watch-written item (writer owns
 * cleanup). A row that cannot be stored leaves the item in the Data Layer; it is read again at the next start and is
 * idempotent by id. Works the same whether the app is open or the process was started by the listener service. With a
 * [PromptCleaner] it also removes the phone's prompt once its answer is stored, and sweeps expired prompts at start and
 * about every [sweepMillis] while the process runs.
 */
class WatchEventProcessor(
    private val source: WatchLinkDataSource,
    private val history: HistoryRepository,
    private val appVersion: String = "0",
    private val cleaner: PromptCleaner? = null,
    private val sweepMillis: Long = SWEEP_MILLIS
) {
    fun start(scope: CoroutineScope) {
        scope.launch { source.publishHello(Hello(Side.PHONE, Protocol.VERSION, appVersion)) }
        scope.launch {
            source.events.collect { event -> process(event) }
        }
        cleaner?.let { scope.launch { sweepWhileRunning(it) } }
    }

    private suspend fun sweepWhileRunning(cleaner: PromptCleaner) {
        while (true) {
            try {
                cleaner.sweepExpired()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The prompt stays in the store and is tried again at the next sweep.
            }
            delay(sweepMillis)
        }
    }

    private suspend fun process(event: WatchEvent) {
        try {
            history.store(event)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        source.markStored(event)
        try {
            cleaner?.answered(event)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The prompt is removed at its expiry instead.
        }
    }

    private companion object {
        const val SWEEP_MILLIS = 60_000L
    }
}
