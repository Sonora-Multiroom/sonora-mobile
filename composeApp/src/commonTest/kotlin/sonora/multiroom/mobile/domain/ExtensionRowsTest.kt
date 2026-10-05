package sonora.multiroom.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionRowsTest {
    private fun ext(
        name: String,
        status: ExtensionStatus = ExtensionStatus.Active,
        connection: ExtensionConnection = ExtensionConnection.NotApplicable,
        id: String = name,
    ) = Extension(id, name, status, connection)

    private fun rows(vararg e: Extension) =
        (extensionRows(ExtensionInventory(true, e.toList())) as ExtensionsContent.Rows).rows

    @Test
    fun theBadgeIsTheStatus() {
        val r = rows(
            ext("a", ExtensionStatus.Active), ext("b", ExtensionStatus.Disabled), ext("c", ExtensionStatus.Rejected),
            ext("d", ExtensionStatus.Inactive), ext("e", ExtensionStatus.Unknown),
        )
        assertEquals(
            listOf(ExtensionBadge.Active, ExtensionBadge.Disabled, ExtensionBadge.Rejected, ExtensionBadge.Inactive, ExtensionBadge.Unknown),
            r.map { it.badge },
        )
    }

    @Test
    fun rejectedDisabledAndInactiveOverrideTheConnection() {
        for (c in ExtensionConnection.entries) {
            val r = rows(
                ext("a", ExtensionStatus.Rejected, c), ext("b", ExtensionStatus.Disabled, c), ext("c", ExtensionStatus.Inactive, c),
            )
            assertEquals(listOf(ExtensionLine.CouldNotLoad, ExtensionLine.TurnedOffInConfig, ExtensionLine.NotInUse), r.map { it.line })
        }
    }

    @Test
    fun anActiveExtensionShowsItsConnection() {
        val r = rows(
            ext("a", connection = ExtensionConnection.Connected), ext("b", connection = ExtensionConnection.Disconnected),
            ext("c", connection = ExtensionConnection.NotApplicable), ext("d", connection = ExtensionConnection.Unknown),
        )
        assertEquals(
            listOf(ExtensionLine.Connected, ExtensionLine.Disconnected, ExtensionLine.NoConnectionNeeded, ExtensionLine.ConnectionUnknown),
            r.map { it.line },
        )
    }

    @Test
    fun rowsAreSortedAtoZByName() =
        assertEquals(listOf("alpha", "Beta", "Zed"), rows(ext("Zed"), ext("alpha"), ext("Beta")).map { it.name })

    @Test
    fun loadingOffAndEmpty() {
        assertEquals(ExtensionsContent.LoadingOff, extensionRows(ExtensionInventory(false, emptyList())))
        assertEquals(ExtensionsContent.LoadingOff, extensionRows(ExtensionInventory(false, listOf(ext("a")))))
        assertEquals(ExtensionsContent.Empty, extensionRows(ExtensionInventory(true, emptyList())))
    }
}
