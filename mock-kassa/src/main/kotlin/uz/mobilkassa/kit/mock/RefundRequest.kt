package uz.mobilkassa.kit.mock

// The kassa's PreReceiptRefundItem / PreReceiptRefundRequest (pospoint dev/v1.0.592).
data class PreReceiptRefundItem(
    val sku: String? = null,
    val amount: Double = 0.0,
    val name: String? = null,
    val mxik: String? = null,
    val barcode: String? = null,
    val mkCode: List<String>? = null,
    val uid: Long? = null,
) {
    val isNamed: Boolean get() = uid != null || listOf(sku, name, mxik, barcode).any { !it.isNullOrBlank() }
}

// `saleId` is the kassa's receipt id (registerRefund's idRefundReceipt), for a sale without a document number.
data class PreReceiptRefundRequest(
    val payCheckNumber: String? = null,
    val salePayCheckNumber: String? = null,
    val items: List<PreReceiptRefundItem>? = null,
    val byCash: Double = 0.0,
    val byCard: Double = 0.0,
    val saleId: Long? = null,
) {
    val hasPayment: Boolean get() = byCash + byCard > 0
}

// PreReceiptRefundRequest.validate: every failure, not only the first.
class RefundRules(private val codes: Codes) {

    fun check(request: PreReceiptRefundRequest) {
        val items = request.items.orEmpty()
        val errors = mutableListOf<String>()
        if (request.payCheckNumber.isNullOrBlank() || request.salePayCheckNumber.isNullOrBlank() && request.saleId == null || items.any { !it.isNamed })
            errors += "JSON_REQUIRED_FIELDS_NOT_FOUND"
        if (request.items != null && request.items.isEmpty()) errors += "RECEIPT_EMPTY"
        if (items.any { it.amount <= 0.0 }) errors += "ITEM_WRONG_AMOUNT"
        if (items.any { !SaleRules.hasValidMarks(it.mkCode, it.amount) }) errors += "ITEM_WRONG_MKCODE"
        if (request.byCash < 0 || request.byCard < 0) errors += "RECEIPT_WRONG_PRICE"
        if (errors.isNotEmpty()) throw codes.failure(*errors.toTypedArray())
    }
}
