import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.openapiGenerator)
}

// The app version (gradle.properties `sonora.versionName`) as a common constant, so every
// platform's Settings footer shows the same version as the Android APK (FR-021a).
abstract class GenerateAppVersion : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.file("ai/sonora/mobile/domain/AppVersionName.kt").get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package ai.sonora.mobile.domain
            |
            |/** Generated from gradle.properties `sonora.versionName`; do not edit. */
            |const val APP_VERSION_NAME: String = "${versionName.get()}"
            |""".trimMargin(),
        )
    }
}

val appVersionOutput = layout.buildDirectory.dir("generated/appVersion/src/commonMain/kotlin")

val generateAppVersion by tasks.registering(GenerateAppVersion::class) {
    versionName.set(providers.gradleProperty("sonora.versionName"))
    outputDir.set(appVersionOutput)
}

kotlin {
    android {
        namespace = "ai.sonora.mobile.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    // iOS targets are declared but never built in the cloud (CLAUDE.md "Decisions").
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            // Generated hub client (never committed, never edited).
            kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/commonMain/kotlin"))
            kotlin.srcDir(appVersionOutput)
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.material3)
                implementation(libs.compose.components.resources)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.navigation3.ui)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.datastore.preferences.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.resources {
    packageOfResClass = "ai.sonora.mobile.resources"
}

// Hub client generation (research R3): v2 operations only, output in build/, never committed.
val openApiOutput = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("multiplatform")
    // As a file: URI, because a Windows path (D:\...) is not a valid spec location.
    inputSpec.set(rootProject.file("api/openapi.json").toURI().toString())
    outputDir.set(openApiOutput.map { it.asFile.absolutePath })
    packageName.set("ai.sonora.mobile.hub.generated")
    // The FILTER normalizer has no `path:` rule in 7.14.0; every /api/v2 operation carries one of
    // these tags, while v1 (`*-controller`) and TTS operations carry others.
    openapiNormalizer.set(
        mapOf("FILTER" to "tag:Routes|Outputs|Inputs|Groups|Master Mute|Playback|Extensions"),
    )
    configOptions.set(
        mapOf(
            "dateLibrary" to "string",
            "enumUnknownDefaultCase" to "false",
            "nonPublicApi" to "true",
            "omitGradleWrapper" to "true",
            "sourceFolder" to "src/commonMain/kotlin",
        ),
    )
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(tasks.named("openApiGenerate"), generateAppVersion)
}
// Anything else that reads the commonMain sources (source jars, resource/metadata tasks).
tasks.matching { it.name.endsWith("SourcesJar") || it.name.startsWith("generateResourceAccessorsFor") }
    .configureEach { dependsOn(tasks.named("openApiGenerate"), generateAppVersion) }

// ui/ and domain/ must never see generated wire types (Constitution I).
abstract class VerifyLayering : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val offenders = sources.files
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("ai.sonora.mobile.hub.generated") }
            .map { it.name }
        check(offenders.isEmpty()) {
            "ui/ and domain/ must not reference ai.sonora.mobile.hub.generated: $offenders"
        }
    }
}

val verifyLayering by tasks.registering(VerifyLayering::class) {
    val base = layout.projectDirectory.dir("src/commonMain/kotlin/ai/sonora/mobile")
    sources.from(
        fileTree(base.dir("ui")) { include("**/*.kt") },
        fileTree(base.dir("domain")) { include("**/*.kt") },
    )
}

tasks.named("check") { dependsOn(verifyLayering) }
