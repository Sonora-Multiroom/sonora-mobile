package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AppVersionTest {
    @Test fun preReleaseSuffixBecomesAStageLabel() = assertEquals("Sonora 0.1.0 · Alpha", versionLabel("0.1.0-alpha"))

    @Test fun stageLabelKeepsTheRestOfTheSuffix() = assertEquals("Sonora 0.2.0 · Beta.1", versionLabel("0.2.0-beta.1"))

    @Test fun releaseVersionHasNoStageLabel() = assertEquals("Sonora 1.0.0", versionLabel("1.0.0"))

    @Test fun blankVersionShowsTheNameOnly() = assertEquals("Sonora", versionLabel(" "))

    @Test fun ciBuildShowsRunNumberAndShortCommit() =
        assertEquals("Build 57 (30c2f6c)", buildLabel("57", "30c2f6c701fae73dee2d9814d7e2a9d04c1d8f88"))

    @Test fun runNumberAloneIsShown() = assertEquals("Build 57", buildLabel("57", ""))

    @Test fun commitAloneIsShown() = assertEquals("Build 30c2f6c", buildLabel(" ", "30c2f6c"))

    @Test fun neitherMeansALocalBuild() = assertEquals("Local build", buildLabel("", ""))

    @Test fun buildVersionIsWiredIn() =
        assertEquals("${versionLabel(APP_VERSION_NAME)} · ${buildLabel(APP_BUILD_NUMBER, APP_COMMIT)}", appVersionLabel())
}
