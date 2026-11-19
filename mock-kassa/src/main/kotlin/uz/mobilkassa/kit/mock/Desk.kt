package uz.mobilkassa.kit.mock

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Duration

// The kassa window, «Предчеки» and the finished pre-receipts, with the calls held on them (the hold rule of the
// erp-autoinput-protocol spec). One lock guards the state; a held call waits outside it.
class Desk(
    private val idle: Duration,
    private val codes: Codes,
    private val scope: CoroutineScope,
    private val onIssued: (Draft, PreReceiptResult) -> Unit = { _, _ -> },
) {

    private enum class Place { OPEN, PARKED, ISSUED, REJECTED }

    private inner class Entry(val draft: Draft) {
        var place = Place.OPEN
        var touched = false
        var result: PreReceiptResult? = null
        var idleJob: Job? = null
        val calls = mutableListOf<CompletableDeferred<PreReceiptResult>>()

        fun summary() = Summary(draft.payCheckNumber, draft.type, place.name, touched, calls.size, draft.lines, Money.round(totalOf(draft)))
    }

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()
    private var open: Entry? = null

    suspend fun send(draft: Draft): PreReceiptResult {
        val (entry, call) = synchronized(lock) { arrive(draft) }
        try {
            return call.await()
        } catch (e: CancellationException) {
            synchronized(lock) { entry.calls.remove(call) }
            throw e
        }
    }

    // Under the lock: the repeat rules, then the window rules.
    private fun arrive(draft: Draft): Pair<Entry, CompletableDeferred<PreReceiptResult>> {
        val existing = entries[draft.payCheckNumber]
        when (existing?.place) {
            Place.ISSUED -> throw codes.failure("FM_RECEIPT_EXECUTED")
            Place.PARKED -> throw codes.failure("PRERECEIPT_WAITING")
            Place.OPEN -> return existing to existing.hold()
            Place.REJECTED, null -> Unit
        }
        val entry = Entry(draft)
        entries.remove(draft.payCheckNumber)
        entries[draft.payCheckNumber] = entry
        val current = open
        if (current != null) {
            if (current.touched) {
                // The cashier is busy with another sale: the new one waits in «Предчеки».
                entry.place = Place.PARKED
                throw codes.failure("PRERECEIPT_WAITING")
            }
            park(current)
        }
        show(entry)
        return entry to entry.hold()
    }

    private fun Entry.hold() = CompletableDeferred<PreReceiptResult>().also { calls += it }

    private fun show(entry: Entry) {
        entry.place = Place.OPEN
        open = entry
        entry.idleJob = scope.launch {
            delay(idle)
            synchronized(lock) { if (open === entry && !entry.touched) park(entry) }
        }
    }

    private fun park(entry: Entry) {
        entry.place = Place.PARKED
        entry.idleJob?.cancel()
        if (open === entry) open = null
        release(entry) { completeExceptionally(codes.failure("PRERECEIPT_WAITING")) }
    }

    private fun release(entry: Entry, how: CompletableDeferred<PreReceiptResult>.() -> Unit) {
        entry.calls.forEach { it.how() }
        entry.calls.clear()
    }

    fun status(payCheckNumber: String): PreReceiptResult = synchronized(lock) {
        val entry = entries[payCheckNumber] ?: throw codes.failure("PRERECEIPT_NOT_FOUND")
        when (entry.place) {
            Place.OPEN, Place.PARKED -> PreReceiptResult(payCheckNumber, PreReceiptResult.WAITING)
            Place.ISSUED -> entry.result!!
            Place.REJECTED -> PreReceiptResult(payCheckNumber, PreReceiptResult.REJECTED)
        }
    }

    fun touch(n: String) = synchronized(lock) {
        val entry = find(n, Place.OPEN)
        entry.touched = true
        entry.idleJob?.cancel()
    }

    fun park(n: String) = synchronized(lock) { park(find(n, Place.OPEN)) }

    fun open(n: String) = synchronized(lock) {
        val entry = find(n, Place.PARKED)
        if (open != null) throw DeskConflict("Окно кассы занято: оформите, отклоните или уберите в «Предчеки» открытый предчек")
        entry.place = Place.OPEN
        entry.touched = true
        open = entry
    }

    fun issue(n: String, changes: Changes?) = synchronized(lock) {
        val entry = find(n, Place.OPEN, Place.PARKED)
        val result = issued(entry.draft, changes)
        entry.place = Place.ISSUED
        entry.result = result
        entry.idleJob?.cancel()
        if (open === entry) open = null
        release(entry) { complete(result) }
        onIssued(entry.draft, result)
    }

    fun reject(n: String) = synchronized(lock) {
        val entry = find(n, Place.OPEN, Place.PARKED)
        entry.place = Place.REJECTED
        entry.idleJob?.cancel()
        if (open === entry) open = null
        release(entry) { completeExceptionally(codes.failure("PRERECEIPT_REJECTED")) }
    }

    fun state(): DeskState = synchronized(lock) {
        DeskState(
            open = open?.summary(),
            parked = entries.values.filter { it.place == Place.PARKED }.map { it.summary() },
            done = entries.values.filter { it.place == Place.ISSUED || it.place == Place.REJECTED }.map { it.summary() },
        )
    }

    // Pre-receipts still waiting for the cashier, in the window or in «Предчеки».
    fun pending(): List<Draft> = synchronized(lock) {
        entries.values.filter { it.place == Place.OPEN || it.place == Place.PARKED }.map { it.draft }
    }

    fun reset() = synchronized(lock) {
        entries.values.forEach { entry ->
            entry.idleJob?.cancel()
            release(entry) { completeExceptionally(codes.failure("PRERECEIPT_WAITING")) }
        }
        entries.clear()
        open = null
    }

    private fun find(n: String, vararg places: Place): Entry {
        val entry = entries[n] ?: throw DeskConflict("Нет предчека $n")
        if (entry.place !in places) throw DeskConflict("Предчек $n сейчас ${PLACE_RU[entry.place]}")
        return entry
    }

    private fun totalOf(draft: Draft) = draft.lines.sumOf { it.price * it.amount - it.discount } - draft.discount

    // The ISSUED answer after the cashier's changes; the cashier may lower or remove a line, never raise it.
    private fun issued(draft: Draft, changes: Changes?): PreReceiptResult {
        var lowered = false
        val items = draft.lines.mapIndexedNotNull { index, line ->
            val change = changes?.items?.firstOrNull { (it.sku != null && it.sku == line.sku) || it.index == index }
            val amount = change?.amount ?: line.amount
            if (amount < 0 || amount > line.amount + EPSILON) throw DeskConflict("Нельзя увеличить количество: ${line.name}")
            if (amount < line.amount - EPSILON) lowered = true
            if (amount <= EPSILON) return@mapIndexedNotNull null
            val discount = Money.round(line.discount * amount / line.amount)
            PreReceiptResultItem(line.sku, line.name, amount, line.price, discount, Money.round(line.price * amount - discount))
        }
        if (items.isEmpty()) throw DeskConflict("Нельзя оформить чек без позиций: отклоните предчек")
        val total = Money.round(items.sumOf { it.sum } - draft.discount)
        val requested = listOfNotNull(
            draft.byCash?.takeIf { it > 0 }?.let { PreReceiptResultPayment("CASH", it) },
            draft.byCard?.takeIf { it > 0 }?.let { PreReceiptResultPayment("CARD", it) },
        )
        val payments = changes?.payments
            ?: requested.takeIf { !lowered && it.isNotEmpty() && draft.refund == null }
            ?: requested.singleOrNull()?.let { listOf(it.copy(amount = total)) }
            ?: listOf(PreReceiptResultPayment("CASH", total))
        val changed = lowered || requested.isNotEmpty() && payments.map { it.type }.toSet() != requested.map { it.type }.toSet()
        return PreReceiptResult(draft.payCheckNumber, PreReceiptResult.ISSUED, draft.type, changed, items, payments, total)
    }

    companion object {
        private const val EPSILON = 1e-9
        private val PLACE_RU = mapOf(
            Place.OPEN to "в окне кассы", Place.PARKED to "в «Предчеках»", Place.ISSUED to "оформлен", Place.REJECTED to "отклонён",
        )
    }
}

// Sums to 2 decimals, half down, as the kassa rounds money.
object Money {
    fun round(value: Double): Double = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_DOWN).toDouble()
}
