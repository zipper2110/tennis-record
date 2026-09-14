package org.litvin.analytics

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** HTTPS-only, daemon-owned, best-effort transport. It never exposes response values to product code. */
class JdkAnalyticsTransport(
    private val endpoint: URI,
    private val onThreadStarted: (Boolean) -> Unit = {}
) : AnalyticsTransport {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread {
            onThreadStarted(Thread.currentThread().isDaemon)
            runnable.run()
        }.apply { isDaemon = true; name = "analytics-delivery" }
    }
    private val client = HttpClient.newBuilder().executor(executor).connectTimeout(Duration.ofSeconds(3)).build()
    @Volatile private var open = true

    override fun send(payload: String) {
        if (!open) return
        runCatching {
            executor.execute {
                if (!open) return@execute
                val request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build()
                client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally { null }
            }
        }
    }

    fun isOpen(): Boolean = open

    override fun close() {
        open = false
        executor.shutdownNow()
    }
}
