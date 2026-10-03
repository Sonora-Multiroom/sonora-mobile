package sonora.multiroom.mobile.domain

/**
 * The Settings footer line (FR-021a). A pre-release suffix becomes the stage label, so
 * `0.1.0-alpha` reads "Sonora 0.1.0 · Alpha" and a plain `1.0.0` drops the label on its own.
 */
fun versionLabel(versionName: String): String {
    val name = versionName.trim()
    if (name.isEmpty()) return "Sonora"
    val number = name.substringBefore('-')
    val stage = name.substringAfter('-', missingDelimiterValue = "")
    return if (stage.isEmpty()) "Sonora $number" else "Sonora $number · ${stage.replaceFirstChar { it.uppercaseChar() }}"
}

/**
 * Which build this is, appended to the footer: "Build 57 (30c2f6c)" for a CI build, "Local build"
 * when the build had neither a run number nor a commit.
 */
fun buildLabel(buildNumber: String, commit: String): String {
    val number = buildNumber.trim()
    val sha = commit.trim().take(7)
    return when {
        number.isNotEmpty() && sha.isNotEmpty() -> "Build $number ($sha)"
        number.isNotEmpty() -> "Build $number"
        sha.isNotEmpty() -> "Build $sha"
        else -> "Local build"
    }
}

/** The constants are generated from gradle.properties and the CI environment. */
fun appVersionLabel(): String =
    "${versionLabel(APP_VERSION_NAME)} · ${buildLabel(APP_BUILD_NUMBER, APP_COMMIT)}"
