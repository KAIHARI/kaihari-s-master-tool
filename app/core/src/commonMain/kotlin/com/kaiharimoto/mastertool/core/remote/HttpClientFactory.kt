package com.kaiharimoto.mastertool.core.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Builds the shared HTTP client.
 *
 * The no-argument form names no engine: each platform contributes exactly one
 * engine artifact (OkHttp on Android/JVM, Darwin on iOS) and Ktor selects it, so
 * this stays in common code. The engine-taking form exists so tests can drive a
 * mock engine through the identical configuration.
 */
object HttpClientFactory {

    /** The full card pool is a large, slow response — the timeout reflects that. */
    private const val REQUEST_TIMEOUT_MS = 180_000L
    private const val CONNECT_TIMEOUT_MS = 30_000L

    fun create(): HttpClient = HttpClient { applyDefaults() }

    fun create(engine: HttpClientEngine): HttpClient = HttpClient(engine) { applyDefaults() }

    private fun HttpClientConfig<*>.applyDefaults() {
        // Non-2xx responses are inspected rather than thrown, so the repository
        // can decide whether a failure is worth surfacing.
        expectSuccess = false

        install(ContentNegotiation) {
            json(YgoProDeckApi.json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }
}
