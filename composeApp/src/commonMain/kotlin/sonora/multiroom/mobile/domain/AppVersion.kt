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

/** [APP_VERSION_NAME] is generated from `sonora.versionName` in gradle.properties. */
fun appVersionLabel(): String = versionLabel(APP_VERSION_NAME)
