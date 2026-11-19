package uz.mobilkassa.kit.mock

import kotlin.math.abs
import kotlin.math.ceil

// The static checks of a sale pre-receipt, in the kassa's order: PreReceiptRequest.validate, then
// ReceiptSaleFullRequest.validate on the same data. A pre-receipt without payment is checked as paid in full.
class SaleRules(private val codes: Codes) {

    fun failure(name: String) = codes.failure(name)

    fun check(request: PreReceiptRequest) {
        // Every line names its position by the ERP's sku (pospoint cf3532aac5).
        if (request.payCheckNumber.isNullOrBlank() || request.lines.any { !it.isComplete || it.sku.isNullOrBlank() })
            throw codes.failure("JSON_REQUIRED_FIELDS_NOT_FOUND")
        val errors = mutableListOf<String>()
        if (request.knownType == null) errors += "RECEIPT_WRONG_TYPE"
        if (request.lines.any { (it.sku?.length ?: 0) > PreReceiptRequest.SKU_MAX }) errors += "ITEM_WRONG_PARAM"
        errors += saleChecks(request)
        val distinct = errors.map { codes.error(it) }.distinctBy { it.code }
        if (distinct.isNotEmpty()) throw RpcFailure(distinct)
    }

    // ReceiptSaleFullRequest.validate with byCash = total when no payment was given.
    private fun saleChecks(request: PreReceiptRequest): List<String> {
        val items = request.lines
        if (items.isEmpty()) return listOf("RECEIPT_EMPTY")
        val byCash = if (request.hasPayment) request.byCash else request.total
        val discount = request.discount ?: 0.0
        val overall = items.sumOf { it.net }
        val errors = mutableListOf<String>()
        if (request.byCard < 0 || byCash < 0) errors += "RECEIPT_WRONG_PRICE"
        if (request.discount != null && request.discount < 0.0) errors += "RECEIPT_WRONG_DISCOUNT"
        if (abs(byCash + request.byCard - (overall - discount)) > PAYMENT_TOLERANCE) errors += "RECEIPT_WRONG_OVERALL"
        if (request.knownType == "REFUND") errors += "RECEIPT_WRONG_TYPE"
        if (!items.all { it.comTin.isNullOrEmpty() || it.comTin.length == 9 || it.comTin.length == 17 }) errors += "ITEM_WRONG_COMTIN"
        if (!items.all { hasValidMarks(it.mkCode, it.amount) }) errors += "ITEM_WRONG_MKCODE"
        if (items.any { it.taxPercent !in TAX_RATES }) errors += "ITEM_WRONG_TAX"
        if (items.any { it.price <= 0.0 }) errors += "ITEM_WRONG_PRICE"
        if (items.any { it.amount <= 0.0 }) errors += "ITEM_WRONG_AMOUNT"
        if (items.any { it.discount != null && (it.discount < 0.0 || it.discount > it.price * it.amount) }) errors += "ITEM_WRONG_DISCOUNT"
        if (request.cardType == SOCIAL_CARD && (request.rrn.isNullOrBlank() || request.socialCardNumber.isNullOrBlank()))
            errors += "SOCIAL_CARD_FIELDS_MISSING"
        return errors
    }

    companion object {
        const val PAYMENT_TOLERANCE = 50.0
        const val SOCIAL_CARD = 3
        const val MARK_MIN = 18
        val TAX_RATES = setOf(0.0, 6.0, 12.0)

        // One mark for the line, or one per unit (ReceiptSaleFullItem.hasValidMkCodes).
        fun hasValidMarks(marks: List<String>?, amount: Double): Boolean =
            marks.isNullOrEmpty() || marks.all { it.length >= MARK_MIN } && (marks.size == 1 || marks.size.toDouble() == ceil(amount))
    }
}
