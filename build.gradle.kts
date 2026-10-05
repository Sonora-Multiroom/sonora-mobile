import groovy.json.JsonSlurper
import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.openapiGenerator) apply false
}

// Local sessions only (cloud sessions cannot reach the hub): replaces api/openapi.json with the
// contract the hub serves. Output keeps the committed format (2-space indent, raw UTF-8, trailing
// newline) so the diff shows only contract changes. Hub: -PhubUrl=http://host:port.
tasks.register("refreshOpenApi") {
    group = "api"
    description = "Downloads the hub's /api-docs into api/openapi.json (local sessions only)."
    val hubUrl = providers.gradleProperty("hubUrl").orElse("http://multiroom.lan:8080")
    val target = layout.projectDirectory.file("api/openapi.json").asFile
    notCompatibleWithConfigurationCache("Network fetch, run by hand")
    doLast {
        val url = hubUrl.get().trimEnd('/') + "/api-docs"
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        val status = connection.responseCode
        if (status != 200) throw GradleException("GET $url answered HTTP $status")
        val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }

        @Suppress("UNCHECKED_CAST")
        val fetched = JsonSlurper().parseText(body) as? Map<String, Any?>
            ?: throw GradleException("GET $url did not return a JSON object")
        if (fetched["openapi"] == null) throw GradleException("GET $url is not an OpenAPI document")

        fun version(doc: Map<*, *>?) = (doc?.get("info") as? Map<*, *>)?.get("version") ?: "?"
        val previous = if (target.exists()) JsonSlurper().parse(target, "UTF-8") as? Map<*, *> else null

        fun quote(s: String) = buildString {
            append('"')
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }

        fun write(value: Any?, indent: String, out: StringBuilder) {
            val inner = "$indent  "
            when (value) {
                null -> out.append("null")
                is String -> out.append(quote(value))
                is Boolean, is Number -> out.append(value.toString())
                is Map<*, *> -> if (value.isEmpty()) out.append("{}") else {
                    out.append("{\n")
                    value.entries.forEachIndexed { i, (k, v) ->
                        out.append(inner).append(quote(k.toString())).append(": ")
                        write(v, inner, out)
                        out.append(if (i < value.size - 1) ",\n" else "\n")
                    }
                    out.append(indent).append('}')
                }
                is List<*> -> if (value.isEmpty()) out.append("[]") else {
                    out.append("[\n")
                    value.forEachIndexed { i, v ->
                        out.append(inner)
                        write(v, inner, out)
                        out.append(if (i < value.size - 1) ",\n" else "\n")
                    }
                    out.append(indent).append(']')
                }
                else -> throw GradleException("Unexpected JSON value ${value::class}")
            }
        }

        val text = StringBuilder().also { write(fetched, "", it) }.append('\n').toString()
        val changed = !target.exists() || target.readText(Charsets.UTF_8) != text
        target.writeText(text, Charsets.UTF_8)
        logger.lifecycle(
            "api/openapi.json: ${version(previous)} -> ${version(fetched)} from $url" +
                if (changed) " (changed, review with git diff)" else " (unchanged)"
        )
    }
}
