package uz.mobilkassa.kit.mock

import kotlin.math.ceil

// A sale a refund can name: issued on this desk (by its document number) or made at the kassa (seeded, by id).
data class Sale(val saleId: Long, val payCheckNumber: String?, val lines: List<DraftLine>)

// Issued sales and issued refunds. What remains on a sale line = sold − issued refunds − refunds still waiting.
class SaleBook {

    private val lock = Any()
    private val sales = mutableListOf<Sale>()
    private val refunds = mutableListOf<Draft>()
    private var nextId = FIRST_SALE_ID

    // Desk.onIssued: a sale becomes refundable as the cashier issued it; a refund records the lines it returned.
    fun record(draft: Draft, result: PreReceiptResult) = synchronized(lock) {
        val lines = issuedLines(draft, result)
        if (draft.refund != null) refunds += draft.copy(lines = lines)
        else sales += Sale(nextId++, draft.payCheckNumber, lines)
    }

    fun seed(saleId: Long, lines: List<DraftLine>) = synchronized(lock) {
        sales.removeAll { it.saleId == saleId }
        sales += Sale(saleId, null, lines)
    }

    fun clear() = synchronized(lock) {
        sales.clear()
        refunds.clear()
        nextId = FIRST_SALE_ID
    }

    fun find(payCheckNumber: String?, saleId: Long?): Sale? = synchronized(lock) {
        if (!payCheckNumber.isNullOrBlank()) sales.lastOrNull { it.payCheckNumber == payCheckNumber }
        else sales.lastOrNull { it.saleId == saleId }
    }

    // Issued and waiting refund lines of a sale line, leaving out the refund document `except` (its own repeat).
    private fun returns(sale: Sale, line: Int, pending: List<Draft>, except: String?): List<DraftLine> = synchronized(lock) {
        (refunds + pending.filter { it.refund != null })
            .filter { it.refund?.saleId == sale.saleId && it.payCheckNumber != except }
            .flatMap { refund -> refund.lines.filter { it.saleLine == line } }
    }

    fun remaining(sale: Sale, line: Int, pending: List<Draft>, except: String?): Double =
        sale.lines[line].amount - returns(sale, line, pending, except).sumOf { it.amount }

    fun takenMarks(sale: Sale, line: Int, pending: List<Draft>, except: String?): Set<String> =
        returns(sale, line, pending, except).flatMap { it.mkCode }.toSet()

    // The draft lines as issued: result items follow the draft order, a removed line has none.
    private fun issuedLines(draft: Draft, result: PreReceiptResult): List<DraftLine> {
        val items = result.items.orEmpty()
        var next = 0
        return draft.lines.mapNotNull { line ->
            val item = items.getOrNull(next)?.takeIf { it.name == line.name && it.sku == line.sku && it.price == line.price }
                ?: return@mapNotNull null
            next++
            line.copy(amount = item.amount, discount = item.discount, mkCode = line.mkCode.take(ceil(item.amount).toInt()))
        }
    }

    companion object {
        const val FIRST_SALE_ID = 1000L
    }
}
