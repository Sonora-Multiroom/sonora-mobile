package ai.sonora.mobile.data

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpEngine(): HttpClientEngineFactory<*> = OkHttp
