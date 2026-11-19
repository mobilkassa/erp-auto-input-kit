package uz.mobilkassa.kit.mock

// The kassa's PreReceiptItem / PreReceiptRequest (pospoint dev/v1.0.592). Every field has a default,
// so Gson builds them through the no-arg constructor and an absent field keeps its default.
data class PreReceiptItem(
    val name: String? = null,
    val amount: Double = 0.0,
    val price: Double = 0.0,
    val mxik: String? = null,
    val ownerType: Int? = null,
    val unit: String? = null,
    val packageCode: Int? = null,
    val taxPercent: Double? = null,
    val mkCode: List<String>? = null,
    val discount: Double? = null,
    val barcode: String? = null,
    val comTin: String? = null,
    val cashSaleType: Int? = null,
    val recipeId: String? = null,
    val sku: String? = null,
) {
    val isComplete: Boolean
        get() = !name.isNullOrBlank() && !mxik.isNullOrBlank() && !unit.isNullOrBlank() &&
            ownerType != null && packageCode != null && taxPercent != null

    val net: Double get() = price * amount - (discount ?: 0.0)
}

data class PreReceiptRequest(
    val payCheckNumber: String? = null,
    val items: List<PreReceiptItem>? = null,
    // The kassa reads an enum; an unknown name reads as null.
    val type: String? = null,
    val byCash: Double = 0.0,
    val byCard: Double = 0.0,
    val discount: Double? = null,
    val cardType: Int? = 0,
    val phoneNumber: String? = null,
    val rrn: String? = null,
    val socialCardNumber: String? = null,
    val mkDetailed: Boolean = false,
    val markValidationSkip: Boolean = false,
) {
    val lines: List<PreReceiptItem> get() = items.orEmpty()

    val hasPayment: Boolean get() = byCash + byCard > 0

    val total: Double get() = lines.sumOf { it.net } - (discount ?: 0.0)

    // The kassa's ReceiptType names; REFUND reads but a sale pre-receipt refuses it.
    val knownType: String? get() = type?.takeIf { it in RECEIPT_TYPES }

    // After SaleRules.check: every line is complete and the type is a sale type.
    fun toDraft() = Draft(
        payCheckNumber = payCheckNumber.orEmpty(),
        type = knownType ?: "SALE",
        lines = lines.map {
            DraftLine(it.sku, it.name.orEmpty(), it.amount, it.price, it.discount ?: 0.0, it.mxik, it.barcode, null, it.mkCode.orEmpty())
        },
        byCash = byCash.takeIf { hasPayment },
        byCard = byCard.takeIf { hasPayment },
        discount = discount ?: 0.0,
    )

    companion object {
        const val SKU_MAX = 64
        val RECEIPT_TYPES = setOf("SALE", "ADVANCE", "CREDIT", "REFUND")
        val SALE_TYPES = setOf("SALE", "ADVANCE", "CREDIT")
    }
}
