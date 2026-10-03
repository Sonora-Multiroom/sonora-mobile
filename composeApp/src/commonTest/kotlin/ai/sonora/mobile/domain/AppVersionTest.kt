package ai.sonora.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AppVersionTest {
    @Test fun preReleaseSuffixBecomesAStageLabel() = assertEquals("Sonora 0.1.0 · Alpha", versionLabel("0.1.0-alpha"))

    @Test fun stageLabelKeepsTheRestOfTheSuffix() = assertEquals("Sonora 0.2.0 · Beta.1", versionLabel("0.2.0-beta.1"))

    @Test fun releaseVersionHasNoStageLabel() = assertEquals("Sonora 1.0.0", versionLabel("1.0.0"))

    @Test fun blankVersionShowsTheNameOnly() = assertEquals("Sonora", versionLabel(" "))

    @Test fun buildVersionIsWiredIn() = assertEquals(versionLabel(APP_VERSION_NAME), appVersionLabel())
}
