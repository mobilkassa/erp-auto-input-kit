package uz.mobilkassa.kit.mock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class DeskTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val desk = Desk(300.milliseconds, Codes.load(), scope)

    private val tea = DraftLine(sku = "2050", name = "Чай чёрный 100 г", amount = 2.0, price = 15000.0, discount = 0.0, mxik = "02101001001000000")

    private fun draft(n: String, byCash: Double? = 30000.0, byCard: Double? = 0.0) =
        Draft(payCheckNumber = n, type = "SALE", lines = listOf(tea), byCash = byCash, byCard = byCard, discount = 0.0)

    // In the supervisor scope: a failed call must reach only its await, as an ERP call does, not cancel the test.
    private fun send(d: Draft): Deferred<PreReceiptResult> = scope.async { desk.send(d) }

    private suspend fun until(condition: () -> Boolean) = withTimeout(2000) { while (!condition()) delay(10) }

    private suspend fun openIs(n: String) = until { desk.state().open?.payCheckNumber == n }

    private suspend fun code(call: Deferred<PreReceiptResult>): Int = assertFailsWith<RpcFailure> { call.await() }.errors.single().code

    private fun teaResult(amount: Double) = PreReceiptResultItem("2050", "Чай чёрный 100 г", amount, 15000.0, 0.0, 15000.0 * amount)

    @Test
    fun `issued sale answers ISSUED with composition`() = runBlocking {
        val call = send(draft("A"))
        openIs("A")
        desk.issue("A", null)
        assertEquals(
            PreReceiptResult("A", "ISSUED", "SALE", false, listOf(teaResult(2.0)), listOf(PreReceiptResultPayment("CASH", 30000.0)), 30000.0),
            call.await(),
        )
    }

    @Test
    fun `no payment given and cashier pays card`() = runBlocking {
        val call = send(draft("A", byCash = null, byCard = null))
        openIs("A")
        desk.issue("A", Changes(payments = listOf(PreReceiptResultPayment("CARD", 30000.0))))
        val result = call.await()
        assertEquals(listOf(PreReceiptResultPayment("CARD", 30000.0)), result.payments)
        assertEquals(false, result.changed)
    }

    @Test
    fun `cashier lowers amount`() = runBlocking {
        val call = send(draft("A"))
        openIs("A")
        desk.issue("A", Changes(items = listOf(LineChange(sku = "2050", amount = 1.0))))
        val result = call.await()
        assertEquals(true, result.changed)
        assertEquals(listOf(teaResult(1.0)), result.items)
        assertEquals(15000.0, result.total)
    }

    @Test
    fun `idle answers 14827 and parks`() = runBlocking {
        val call = send(draft("A"))
        assertEquals(14827, code(call))
        assertEquals(listOf("A"), desk.state().parked.map { it.payCheckNumber })
        assertEquals("WAITING", desk.status("A").status)
    }

    @Test
    fun `touched pre-receipt is held past idle`() = runBlocking {
        val call = send(draft("A"))
        openIs("A")
        desk.touch("A")
        delay(600)
        assertEquals("A", desk.state().open?.payCheckNumber)
        desk.issue("A", null)
        assertEquals("ISSUED", call.await().status)
    }

    @Test
    fun `second document while first untouched`() = runBlocking {
        val first = send(draft("A"))
        openIs("A")
        val second = send(draft("B"))
        assertEquals(14827, code(first))
        openIs("B")
        assertEquals(listOf("A"), desk.state().parked.map { it.payCheckNumber })
        desk.issue("B", null)
        assertEquals("ISSUED", second.await().status)
    }

    @Test
    fun `second document while first touched`() = runBlocking {
        val first = send(draft("A"))
        openIs("A")
        desk.touch("A")
        val second = send(draft("B"))
        assertEquals(14827, code(second))
        assertEquals("A", desk.state().open?.payCheckNumber)
        desk.issue("A", null)
        assertEquals("ISSUED", first.await().status)
    }

    @Test
    fun `repeat while open joins the same result`() = runBlocking {
        val first = send(draft("A"))
        openIs("A")
        desk.touch("A")
        val repeat = send(draft("A"))
        until { desk.state().open?.calls == 2 }
        desk.issue("A", null)
        assertEquals(first.await(), repeat.await())
        assertEquals(1, desk.state().done.size)
    }

    @Test
    fun `repeat while parked answers 14827 at once`() = runBlocking {
        val call = send(draft("A"))
        assertEquals(14827, code(call))
        assertEquals(14827, code(send(draft("A"))))
        assertEquals(listOf("A"), desk.state().parked.map { it.payCheckNumber })
    }

    @Test
    fun `repeat after issue answers 14824`() = runBlocking {
        val call = send(draft("A"))
        openIs("A")
        desk.issue("A", null)
        call.await()
        assertEquals(14824, code(send(draft("A"))))
    }

    @Test
    fun `reject answers 14828, status REJECTED, re-send accepted as new`() = runBlocking {
        val call = send(draft("A"))
        openIs("A")
        desk.reject("A")
        assertEquals(14828, code(call))
        assertEquals("REJECTED", desk.status("A").status)
        val again = send(draft("A", byCash = 15000.0).copy(lines = listOf(tea.copy(amount = 1.0))))
        openIs("A")
        desk.issue("A", null)
        assertEquals(15000.0, again.await().total)
    }

    @Test
    fun `status of unknown number answers 14829`() {
        assertEquals(14829, assertFailsWith<RpcFailure> { desk.status("NONE") }.errors.single().code)
    }

    @Test
    fun `caller cancelled mid-hold keeps the pre-receipt`() = runBlocking {
        val call = send(draft("A"))
        openIs("A")
        desk.touch("A")
        call.cancelAndJoin()
        assertEquals("WAITING", desk.status("A").status)
        desk.issue("A", null)
        assertEquals("ISSUED", desk.status("A").status)
        assertEquals(30000.0, desk.status("A").total)
    }

    @Test
    fun `issue raising an amount is refused`() = runBlocking {
        send(draft("A"))
        openIs("A")
        assertFailsWith<DeskConflict> { desk.issue("A", Changes(items = listOf(LineChange(index = 0, amount = 3.0)))) }
        assertEquals("A", desk.state().open?.payCheckNumber)
    }

    @Test
    fun `issue of a parked pre-receipt answers its status`() = runBlocking {
        assertEquals(14827, code(send(draft("A"))))
        desk.issue("A", null)
        assertEquals("ISSUED", desk.status("A").status)
    }
}
