package uz.mobilkassa.kit.mock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SaleRulesTest {

    private val rules = SaleRules(Codes.load())

    private val tea = PreReceiptItem(
        name = "Чай чёрный 100 г", amount = 2.0, price = 15000.0, mxik = "02101001001000000",
        ownerType = 0, unit = "1", packageCode = 1500, taxPercent = 12.0, sku = "2050",
    )

    private fun sale(vararg items: PreReceiptItem = arrayOf(tea), byCash: Double = 30000.0, byCard: Double = 0.0, type: String? = "SALE") =
        PreReceiptRequest(payCheckNumber = "INV-1", items = items.toList(), type = type, byCash = byCash, byCard = byCard)

    private fun codes(request: PreReceiptRequest): List<Int> = assertFailsWith<RpcFailure> { rules.check(request) }.errors.map { it.code }

    private fun accepted(request: PreReceiptRequest) = rules.check(request)

    @Test
    fun `no document number answers 14804 alone`() {
        assertEquals(listOf(14804), codes(sale(type = null).copy(payCheckNumber = null)))
    }

    @Test
    fun `an item missing a product field answers 14804 alone`() {
        listOf(
            tea.copy(name = null), tea.copy(mxik = ""), tea.copy(unit = null),
            tea.copy(ownerType = null), tea.copy(packageCode = null), tea.copy(taxPercent = null),
        ).forEach { assertEquals(listOf(14804), codes(sale(it, type = null)), it.toString()) }
    }

    // pospoint cf3532aac5: every pre-receipt item names its position by the ERP's sku.
    @Test
    fun `an item without a sku answers 14804 alone`() {
        assertEquals(listOf(14804), codes(sale(tea.copy(sku = null), type = null)))
        assertEquals(listOf(14804), codes(sale(tea.copy(sku = " "))))
        assertEquals(listOf(14804), codes(sale(tea, tea.copy(sku = null), byCash = 60000.0)))
    }

    @Test
    fun `no type answers 14808`() {
        assertEquals(listOf(14808), codes(sale(type = null)))
    }

    @Test
    fun `type REFUND or unknown answers 14808`() {
        assertEquals(listOf(14808), codes(sale(type = "REFUND")))
        assertEquals(listOf(14808), codes(sale(type = "FOO")))
    }

    @Test
    fun `ADVANCE and CREDIT are accepted`() {
        accepted(sale(type = "ADVANCE"))
        accepted(sale(type = "CREDIT"))
    }

    @Test
    fun `sku of 65 characters answers 14818`() {
        assertEquals(listOf(14818), codes(sale(tea.copy(sku = "x".repeat(65)))))
        accepted(sale(tea.copy(sku = "x".repeat(64))))
    }

    @Test
    fun `no items answers 14810`() {
        assertEquals(listOf(14810), codes(sale(*emptyArray())))
    }

    @Test
    fun `negative payment answers 14805`() {
        assertEquals(listOf(14805), codes(sale(byCash = -1.0, byCard = 30001.0)))
    }

    @Test
    fun `negative receipt discount answers 14806`() {
        // A discount of -1 raises the total to 30001: still within 50 of the 30000 paid.
        assertEquals(listOf(14806), codes(sale().copy(discount = -1.0)))
    }

    @Test
    fun `payment off the total by more than 50 answers 14807`() {
        assertEquals(listOf(14807), codes(sale(byCash = 29000.0)))
        accepted(sale(byCash = 29960.0))
        accepted(sale(byCash = 10000.0, byCard = 20040.0))
    }

    @Test
    fun `no payment at all is accepted`() {
        accepted(sale(byCash = 0.0))
    }

    @Test
    fun `comTin must have 9 or 17 characters`() {
        assertEquals(listOf(14816), codes(sale(tea.copy(comTin = "1234567890"))))
        accepted(sale(tea.copy(comTin = "123456789")))
        accepted(sale(tea.copy(comTin = "12345678901234567")))
    }

    @Test
    fun `mark codes need 18 characters and one per line or one per unit`() {
        val mark = "0104780001000017215abcdefg"
        assertEquals(listOf(14817), codes(sale(tea.copy(mkCode = listOf("x".repeat(17))))))
        assertEquals(listOf(14817), codes(sale(tea.copy(amount = 3.0, mkCode = listOf(mark, mark)), byCash = 45000.0)))
        accepted(sale(tea.copy(amount = 2.5, mkCode = listOf(mark, mark, mark)), byCash = 37500.0))
        accepted(sale(tea.copy(mkCode = listOf(mark))))
    }

    @Test
    fun `tax must be 0, 6 or 12`() {
        assertEquals(listOf(14814), codes(sale(tea.copy(taxPercent = -1.0))))
        assertEquals(listOf(14814), codes(sale(tea.copy(taxPercent = 15.0))))
        listOf(0.0, 6.0, 12.0).forEach { accepted(sale(tea.copy(taxPercent = it))) }
    }

    @Test
    fun `price and amount must be above zero`() {
        assertEquals(listOf(14807, 14812), codes(sale(tea.copy(price = 0.0))))
        assertEquals(listOf(14807, 14813), codes(sale(tea.copy(amount = 0.0))))
    }

    @Test
    fun `item discount above the item sum answers 14815`() {
        assertEquals(listOf(14807, 14815), codes(sale(tea.copy(discount = 30001.0))))
    }

    @Test
    fun `social card without rrn answers 14823`() {
        assertEquals(listOf(14823), codes(sale().copy(cardType = 3, socialCardNumber = "8600")))
    }

    @Test
    fun `several failures answer each code once in the kassa order`() {
        assertEquals(listOf(14808, 14812, 14813), codes(sale(tea.copy(price = 0.0), tea.copy(amount = 0.0), byCash = 0.0, type = null)))
    }
}
