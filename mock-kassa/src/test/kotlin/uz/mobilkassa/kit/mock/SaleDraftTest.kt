package uz.mobilkassa.kit.mock

import kotlin.test.Test
import kotlin.test.assertEquals

class SaleDraftTest {

    private val tea = PreReceiptItem(
        name = "Чай", amount = 2.0, price = 15000.0, mxik = "02101001001000000", ownerType = 0, unit = "1",
        packageCode = 1500, taxPercent = 12.0, sku = "2050", barcode = "4780001000017", mkCode = listOf("m".repeat(18)), discount = 100.0,
    )

    @Test
    fun `a checked sale becomes a draft line for line`() {
        val draft = PreReceiptRequest(payCheckNumber = "A", items = listOf(tea), type = "ADVANCE", byCash = 29900.0, discount = 50.0).toDraft()
        assertEquals(
            Draft(
                "A", "ADVANCE",
                listOf(DraftLine("2050", "Чай", 2.0, 15000.0, 100.0, "02101001001000000", "4780001000017", null, listOf("m".repeat(18)))),
                byCash = 29900.0, byCard = 0.0, discount = 50.0,
            ),
            draft,
        )
    }

    @Test
    fun `a sale without payment has no requested payment`() {
        val draft = PreReceiptRequest(payCheckNumber = "A", items = listOf(tea), type = "SALE").toDraft()
        assertEquals(null to null, draft.byCash to draft.byCard)
    }
}
