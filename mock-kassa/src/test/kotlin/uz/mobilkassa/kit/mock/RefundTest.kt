package uz.mobilkassa.kit.mock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class RefundTest {

    private val codes = Codes.load()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val book = SaleBook()
    private val desk = Desk(10.seconds, codes, scope, book::record)
    private val matcher = RefundMatcher(book, codes, desk::pending)
    private val rules = RefundRules(codes)

    private val mark1 = "0104780001000017215aaaaaaaa"
    private val mark2 = "0104780001000017215bbbbbbbb"
    private val tea = DraftLine(sku = "2050", name = "Чай чёрный 100 г", amount = 2.0, price = 15000.0, mxik = "02101001001000000", barcode = "4780001000017")

    private suspend fun until(condition: () -> Boolean) = withTimeout(2000) { while (!condition()) delay(10) }

    private fun send(d: Draft): Deferred<PreReceiptResult> = scope.async { desk.send(d) }

    private suspend fun issue(d: Draft, changes: Changes? = null): PreReceiptResult {
        val call = send(d)
        until { desk.state().open?.payCheckNumber == d.payCheckNumber }
        desk.issue(d.payCheckNumber, changes)
        return call.await()
    }

    private suspend fun sell(n: String, vararg lines: DraftLine = arrayOf(tea)) =
        issue(Draft(n, "SALE", lines.toList(), byCash = lines.sumOf { it.price * it.amount }, byCard = 0.0))

    private fun refund(n: String, sale: String = "INV-1", vararg items: PreReceiptRefundItem, byCash: Double = 0.0) =
        PreReceiptRefundRequest(payCheckNumber = n, salePayCheckNumber = sale, items = items.toList().ifEmpty { null }, byCash = byCash)

    private fun failure(block: () -> Unit): Int = assertFailsWith<RpcFailure> { block() }.errors.single().code

    @Test
    fun `partial refund by sku`() = runBlocking {
        sell("INV-1")
        val result = issue(matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 1.0)))))
        assertEquals("REFUND", result.type)
        assertEquals(listOf(PreReceiptResultItem("2050", tea.name, 1.0, 15000.0, 0.0, 15000.0)), result.items)
        assertEquals(false, result.changed)
    }

    @Test
    fun `no items refunds the whole remainder`() = runBlocking {
        sell("INV-1")
        issue(matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 1.0)))))
        val draft = matcher.draft(refund("RET-2"))
        assertEquals(listOf(1.0), draft.lines.map { it.amount })
    }

    @Test
    fun `line matching order`() {
        book.seed(
            1,
            listOf(
                DraftLine(sku = "2050", name = "Чай", amount = 5.0, price = 100.0, mxik = "M1", barcode = "B1", uid = 1),
                DraftLine(sku = "2051", name = "Чай зелёный", amount = 5.0, price = 100.0, mxik = "M1", barcode = "B2", uid = 2),
                DraftLine(name = "Сахар", amount = 5.0, price = 100.0, mxik = "M2", barcode = "B3", uid = 3),
            ),
        )
        fun lineOf(item: PreReceiptRefundItem) =
            matcher.draft(PreReceiptRefundRequest(payCheckNumber = "R", saleId = 1, items = listOf(item))).lines.single().saleLine
        assertEquals(1, lineOf(PreReceiptRefundItem(sku = "2051", uid = 1, amount = 1.0)))
        assertEquals(2, lineOf(PreReceiptRefundItem(uid = 3, mxik = "M1", amount = 1.0)))
        assertEquals(1, lineOf(PreReceiptRefundItem(mxik = "M1", name = "Чай зелёный", amount = 1.0)))
        assertEquals(2, lineOf(PreReceiptRefundItem(barcode = "B3", name = "Чай", amount = 1.0)))
        assertEquals(2, lineOf(PreReceiptRefundItem(name = "Сахар", amount = 1.0)))
        assertEquals(0, lineOf(PreReceiptRefundItem(sku = "9999", name = "Чай", amount = 1.0)))
    }

    @Test
    fun `refund by saleId of a seeded sale, line by barcode`() = runBlocking {
        book.seed(7, listOf(DraftLine(name = "Сахар 1 кг", amount = 2.0, price = 12000.0, mxik = "10701001001000000", barcode = "4780001000017", uid = 11)))
        val result = issue(matcher.draft(PreReceiptRefundRequest(payCheckNumber = "RET-7", saleId = 7, items = listOf(PreReceiptRefundItem(barcode = "4780001000017", amount = 1.0)))))
        assertEquals(listOf(PreReceiptResultItem(null, "Сахар 1 кг", 1.0, 12000.0, 0.0, 12000.0)), result.items)
    }

    @Test
    fun `unknown sale answers 14829`() {
        assertEquals(14829, failure { matcher.draft(refund("RET-1", sale = "NONE")) })
    }

    @Test
    fun `item that names no line answers 14811`() = runBlocking {
        sell("INV-1")
        assertEquals(14811, failure { matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(name = "Кофе", amount = 1.0)))) })
    }

    @Test
    fun `over the remainder answers 14807, counting a waiting refund`() = runBlocking {
        sell("INV-1")
        assertEquals(14807, failure { matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 3.0)))) })
        send(matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 2.0)))))
        until { desk.state().open?.payCheckNumber == "RET-1" }
        assertEquals(14807, failure { matcher.draft(refund("RET-2", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 1.0)))) })
    }

    @Test
    fun `a repeat of a waiting refund is not counted against itself`() = runBlocking {
        sell("INV-1")
        send(matcher.draft(refund("RET-1")))
        until { desk.state().open?.payCheckNumber == "RET-1" }
        assertEquals(listOf(2.0), matcher.draft(refund("RET-1")).lines.map { it.amount })
    }

    @Test
    fun `marks name the line, marks already refunded answer 14811`() = runBlocking {
        sell("INV-1", tea.copy(mkCode = listOf(mark1, mark2)))
        val first = matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 1.0, mkCode = listOf(mark1)))))
        assertEquals(listOf(mark1), first.lines.single().mkCode)
        issue(first)
        assertEquals(14811, failure { matcher.draft(refund("RET-2", items = arrayOf(PreReceiptRefundItem(amount = 1.0, mkCode = listOf(mark1), name = tea.name)))) })
        assertEquals(listOf(mark2), matcher.draft(refund("RET-3", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 1.0)))).lines.single().mkCode)
    }

    @Test
    fun `refund payment off the refund total answers 14807`() = runBlocking {
        sell("INV-1")
        assertEquals(14807, failure { matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 1.0)), byCash = 10000.0)) })
    }

    @Test
    fun `refund paid another way answers changed`() = runBlocking {
        sell("INV-1")
        val draft = matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 1.0)), byCash = 15000.0))
        val result = issue(draft, Changes(payments = listOf(PreReceiptResultPayment("CARD", 15000.0))))
        assertEquals(listOf(PreReceiptResultPayment("CARD", 15000.0)), result.payments)
        assertEquals(true, result.changed)
    }

    @Test
    fun `cashier removed a line`() = runBlocking {
        sell("INV-1", tea.copy(amount = 1.0), tea.copy(sku = "1042", name = "Кофе", amount = 1.0), tea.copy(sku = "3077", name = "Сахар", amount = 1.0))
        val items = listOf("2050", "1042", "3077").map { PreReceiptRefundItem(sku = it, amount = 1.0) }.toTypedArray()
        val result = issue(matcher.draft(refund("RET-1", items = items)), Changes(items = listOf(LineChange(sku = "1042", amount = 0.0))))
        assertEquals(listOf("2050", "3077"), result.items!!.map { it.sku })
        assertEquals(true, result.changed)
    }

    @Test
    fun `asking again for what was not refunded`() = runBlocking {
        sell("INV-1")
        issue(matcher.draft(refund("RET-1", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 2.0)))), Changes(items = listOf(LineChange(sku = "2050", amount = 1.0))))
        assertEquals(listOf(1.0), matcher.draft(refund("RET-2", items = arrayOf(PreReceiptRefundItem(sku = "2050", amount = 1.0)))).lines.map { it.amount })
    }

    @Test
    fun `refund rules`() {
        fun codes(r: PreReceiptRefundRequest) = assertFailsWith<RpcFailure> { rules.check(r) }.errors.map { it.code }
        assertEquals(listOf(14804), codes(PreReceiptRefundRequest(salePayCheckNumber = "INV-1")))
        assertEquals(listOf(14804), codes(PreReceiptRefundRequest(payCheckNumber = "R")))
        assertEquals(listOf(14804), codes(refund("R", items = arrayOf(PreReceiptRefundItem(amount = 1.0)))))
        assertEquals(listOf(14810), codes(PreReceiptRefundRequest(payCheckNumber = "R", saleId = 1, items = emptyList())))
        assertEquals(listOf(14813), codes(refund("R", items = arrayOf(PreReceiptRefundItem(sku = "1", amount = 0.0)))))
        assertEquals(listOf(14817), codes(refund("R", items = arrayOf(PreReceiptRefundItem(sku = "1", amount = 1.0, mkCode = listOf("short"))))))
        assertEquals(listOf(14805), codes(refund("R", byCash = -1.0)))
        rules.check(refund("R", items = arrayOf(PreReceiptRefundItem(uid = 5, amount = 1.0))))
    }
}
