package sonora.multiroom.mobile.data

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun httpEngine(): HttpClientEngineFactory<*> = Darwin
