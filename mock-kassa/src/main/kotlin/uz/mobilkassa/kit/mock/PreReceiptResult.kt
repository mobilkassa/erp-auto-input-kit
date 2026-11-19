package uz.mobilkassa.kit.mock

data class PreReceiptResultItem(
    val sku: String?,
    val name: String,
    val amount: Double,
    val price: Double,
    val discount: Double,
    val sum: Double,
)

data class PreReceiptResultPayment(val type: String, val amount: Double)

// The kassa's answer: no receipt number, FM or KKM number, shift, fiscal time, QR link or fiscal sign.
data class PreReceiptResult(
    val payCheckNumber: String,
    val status: String,
    val type: String? = null,
    val changed: Boolean? = null,
    val items: List<PreReceiptResultItem>? = null,
    val payments: List<PreReceiptResultPayment>? = null,
    val total: Double? = null,
) {
    companion object {
        const val WAITING = "WAITING"
        const val ISSUED = "ISSUED"
        const val REJECTED = "REJECTED"
    }
}

// One line of a pre-receipt as the cashier sees it. `saleLine` is the index of the sale line a refund line returns.
data class DraftLine(
    val sku: String? = null,
    val name: String,
    val amount: Double,
    val price: Double,
    val discount: Double = 0.0,
    val mxik: String? = null,
    val barcode: String? = null,
    val uid: Long? = null,
    val mkCode: List<String> = emptyList(),
    val saleLine: Int? = null,
)

data class RefundLink(val saleId: Long)

// A pre-receipt on the desk. byCash/byCard are what the ERP asked for (null = not given).
data class Draft(
    val payCheckNumber: String,
    val type: String,
    val lines: List<DraftLine>,
    val byCash: Double? = null,
    val byCard: Double? = null,
    val discount: Double = 0.0,
    val refund: RefundLink? = null,
)

data class LineChange(val sku: String? = null, val index: Int? = null, val amount: Double)

data class Changes(val items: List<LineChange>? = null, val payments: List<PreReceiptResultPayment>? = null)

class DeskConflict(message: String) : Exception(message)

data class Summary(
    val payCheckNumber: String,
    val type: String,
    val status: String,
    val touched: Boolean,
    val calls: Int,
    val lines: List<DraftLine>,
    val total: Double,
)

data class DeskState(val open: Summary?, val parked: List<Summary>, val done: List<Summary>)
