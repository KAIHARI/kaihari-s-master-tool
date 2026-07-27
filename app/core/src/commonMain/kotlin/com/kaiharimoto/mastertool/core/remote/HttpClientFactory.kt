package com.kaiharimoto.mastertool.core.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Builds the shared HTTP client.
 *
 * No engine is named here: each platform contributes exactly one engine
 * artifact (OkHttp on Android/JVM, Darwin on iOS) and Ktor selects it, so this
 * stays in common code.
 */
object HttpClientFactory {

    /** The full card pool is a large, slow response — the timeout reflects that. */
    private const val REQUEST_TIMEOUT_MS = 180_000L
    private const val CONNECT_TIMEOUT_MS = 30_000L

    fun create(): HttpClient = HttpClient {
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
