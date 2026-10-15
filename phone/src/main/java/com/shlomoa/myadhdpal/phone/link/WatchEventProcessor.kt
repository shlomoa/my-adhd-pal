package com.shlomoa.myadhdpal.phone.link

import com.shlomoa.myadhdpal.phone.data.HistoryRepository
import com.shlomoa.myadhdpal.phone.data.WatchEvent
import com.shlomoa.myadhdpal.phone.data.WatchLinkDataSource
import com.shlomoa.myadhdpal.protocol.Hello
import com.shlomoa.myadhdpal.protocol.Protocol
import com.shlomoa.myadhdpal.protocol.Side
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Stores what the watch sends and writes the phone's own hello once at start. For each event the history row is stored
 * first and only then is the event marked stored, which makes the source delete the watch-written item (writer owns
 * cleanup). A row that cannot be stored leaves the item in the Data Layer; it is read again at the next start and is
 * idempotent by id. Works the same whether the app is open or the process was started by the listener service.
 */
class WatchEventProcessor(
    private val source: WatchLinkDataSource,
    private val history: HistoryRepository,
    private val appVersion: String = "0"
) {
    fun start(scope: CoroutineScope) {
        scope.launch { source.publishHello(Hello(Side.PHONE, Protocol.VERSION, appVersion)) }
        scope.launch {
            source.events.collect { event -> process(event) }
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
    }
}
