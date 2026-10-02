package ai.sonora.mobile.domain

import kotlin.jvm.JvmInline

/** The hub's normalised base URL, e.g. `http://multiroom.lan:8080`. */
@JvmInline
value class HubAddress(val baseUrl: String)
