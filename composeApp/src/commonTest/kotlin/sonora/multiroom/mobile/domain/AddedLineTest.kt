package sonora.multiroom.mobile.domain

import sonora.multiroom.mobile.ui.settings.addedLineText
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddedLineTest {
    private val kyiv = TimeZone.of("Europe/Kyiv")

    /** Builds the line of a runtime source added at [createdAt], seen at [now], in Kyiv. */
    private fun line(createdAt: String?, now: String, off: Boolean = false, autoRemove: Boolean = false): String? {
        val s = snapshot(sources = listOf(source("x", "X", SourceOrigin.Runtime, "https://soundcloud.com/x", enabled = !off, autoRemove = autoRemove, createdAt = createdAt?.let(Instant::parse))))
        val row = SettingsBuilder.build(s, Instant.parse(now), kyiv).runtimeSources.single()
        return addedLineText(row.added)
    }

    @Test
    fun sameLocalDateIsToday() =
        assertEquals("Added today 14:30", line("2026-10-04T11:30:00Z", "2026-10-04T18:00:00Z"))

    @Test
    fun theDayBeforeIsYesterday() =
        assertEquals("Added yesterday 09:12", line("2026-10-03T06:12:00Z", "2026-10-04T18:00:00Z"))

    @Test
    fun yesterdayIsDecidedOnLocalDatesNotUtc() {
        // 23:30 UTC on the 3rd is 02:30 on the 4th in Kyiv (+3), so at 00:10 Kyiv on the 4th (21:10 UTC
        // on the 3rd) the same UTC day is already "yesterday" locally.
        assertEquals("Added yesterday 23:30", line("2026-10-03T20:30:00Z", "2026-10-03T21:10:00Z"))
        assertEquals("Added today 02:30", line("2026-10-03T23:30:00Z", "2026-10-04T10:00:00Z"))
        assertEquals("Added yesterday 23:30", line("2026-10-03T20:30:00Z", "2026-10-04T10:00:00Z"))
    }

    @Test
    fun olderIsDayMonthAndTime() =
        assertEquals("Added 2 Oct 14:30", line("2026-10-02T11:30:00Z", "2026-10-04T18:00:00Z"))

    @Test
    fun theWallTimeStaysLocalAcrossTheDstChange() {
        // Kyiv leaves summer time on Sunday 25 Oct 2026 (01:00 UTC): +3 before, +2 after.
        assertEquals("Added 24 Oct 12:00", line("2026-10-24T09:00:00Z", "2026-10-30T10:00:00Z"))
        assertEquals("Added 26 Oct 12:00", line("2026-10-26T10:00:00Z", "2026-10-30T10:00:00Z"))
    }

    @Test
    fun autoRemoveAppendsItsNote() =
        assertEquals("Added today 14:30 · removed when it stops", line("2026-10-04T11:30:00Z", "2026-10-04T18:00:00Z", autoRemove = true))

    @Test
    fun turnedOffPrependsOff() =
        assertEquals("Off · Added today 14:30", line("2026-10-04T11:30:00Z", "2026-10-04T18:00:00Z", off = true))

    @Test
    fun anUndatedSourceDropsTheDatedPartAndKeepsTheRest() {
        assertEquals("Off · removed when it stops", line(null, "2026-10-04T18:00:00Z", off = true, autoRemove = true))
        assertEquals("Off", line(null, "2026-10-04T18:00:00Z", off = true))
        assertEquals("removed when it stops", line(null, "2026-10-04T18:00:00Z", autoRemove = true))
        assertNull(line(null, "2026-10-04T18:00:00Z"))
    }
}
