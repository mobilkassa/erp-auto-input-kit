package uz.mobilkassa.kit.mock

import kotlin.math.abs
import kotlin.math.ceil

// Turns a refund request into a refund draft against its sale (the "Refund by sale document" requirement):
// finds the sale, names each line by sku, then uid, then mxik or barcode (name breaks a tie), then name.
class RefundMatcher(
    private val book: SaleBook,
    private val codes: Codes,
    private val pending: () -> List<Draft>,
) {

    fun draft(request: PreReceiptRefundRequest): Draft {
        val sale = book.find(request.salePayCheckNumber, request.saleId) ?: throw codes.failure("PRERECEIPT_NOT_FOUND")
        val waiting = pending()
        val own = request.payCheckNumber
        val left = sale.lines.indices.map { book.remaining(sale, it, waiting, own) }.toMutableList()
        val taken = sale.lines.indices.map { book.takenMarks(sale, it, waiting, own).toMutableSet() }

        val lines = if (request.items == null) {
            sale.lines.indices.filter { left[it] > EPSILON }.map { line(sale, it, left[it], null, taken[it]) }
                .ifEmpty { throw codes.failure("RECEIPT_WRONG_OVERALL") }
        } else {
            request.items.map { item ->
                val index = match(sale, item, left, taken) ?: throw codes.failure("ITEM_NOT_FOUND")
                if (item.amount > left[index] + EPSILON) throw codes.failure("RECEIPT_WRONG_OVERALL")
                left[index] -= item.amount
                line(sale, index, item.amount, item.mkCode, taken[index]).also { taken[index] += it.mkCode }
            }
        }

        val total = lines.sumOf { it.price * it.amount }
        if (request.hasPayment && abs(request.byCash + request.byCard - total) > SaleRules.PAYMENT_TOLERANCE)
            throw codes.failure("RECEIPT_WRONG_OVERALL")
        return Draft(
            payCheckNumber = request.payCheckNumber.orEmpty(),
            type = "REFUND",
            lines = lines,
            byCash = request.byCash.takeIf { request.hasPayment },
            byCard = request.byCard.takeIf { request.hasPayment },
            refund = RefundLink(sale.saleId),
        )
    }

    private fun match(sale: Sale, item: PreReceiptRefundItem, left: List<Double>, taken: List<Set<String>>): Int? {
        val marks = item.mkCode.orEmpty()
        // With marks, only a line that holds them and has not returned or reserved them.
        val eligible = sale.lines.indices.filter { i ->
            marks.isEmpty() || sale.lines[i].mkCode.containsAll(marks) && marks.none { it in taken[i] }
        }
        val ways = listOf<(DraftLine) -> Boolean>(
            { !item.sku.isNullOrBlank() && it.sku == item.sku },
            { item.uid != null && it.uid == item.uid },
            { !item.mxik.isNullOrBlank() && it.mxik == item.mxik || !item.barcode.isNullOrBlank() && it.barcode == item.barcode },
            { !item.name.isNullOrBlank() && it.name == item.name },
        )
        for ((way, test) in ways.withIndex()) {
            var found = eligible.filter { test(sale.lines[it]) }
            if (found.isEmpty()) continue
            if (way == MXIK_OR_BARCODE && found.size > 1 && !item.name.isNullOrBlank())
                found = found.filter { sale.lines[it].name == item.name }.ifEmpty { found }
            return found.firstOrNull { left[it] > EPSILON } ?: found.first()
        }
        return null
    }

    // A refund line at the sale line's net unit price; without marks given, the units the sale still holds.
    private fun line(sale: Sale, index: Int, amount: Double, marks: List<String>?, taken: Set<String>): DraftLine {
        val sold = sale.lines[index]
        val unit = Money.round((sold.price * sold.amount - sold.discount) / sold.amount)
        val units = marks ?: sold.mkCode.filter { it !in taken }.take(ceil(amount).toInt())
        return sold.copy(amount = amount, price = unit, discount = 0.0, mkCode = units, saleLine = index)
    }

    companion object {
        private const val EPSILON = 1e-9
        private const val MXIK_OR_BARCODE = 2
    }
}
