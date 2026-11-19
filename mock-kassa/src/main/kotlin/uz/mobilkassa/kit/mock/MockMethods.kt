package uz.mobilkassa.kit.mock

import com.google.gson.Gson
import com.google.gson.JsonElement

// The three auto-input methods: static rules in validate (answer "id":0), the desk in call.
class MockMethods(
    private val gson: Gson,
    private val saleRules: SaleRules,
    private val refundRules: RefundRules,
    private val matcher: RefundMatcher,
    private val desk: Desk,
) : AutoInputMethods {

    override fun has(method: String) = method in NAMES

    override fun validate(method: String, params: JsonElement?) {
        when (method) {
            SALE -> saleRules.check(read(params, PreReceiptRequest::class.java))
            REFUND -> refundRules.check(read(params, PreReceiptRefundRequest::class.java))
            STATUS -> if (read(params, StatusRequest::class.java).payCheckNumber.isNullOrBlank()) throw failure("JSON_REQUIRED_FIELDS_NOT_FOUND")
        }
    }

    override suspend fun call(method: String, params: JsonElement?): Any = when (method) {
        SALE -> desk.send(read(params, PreReceiptRequest::class.java).toDraft())
        REFUND -> desk.send(matcher.draft(read(params, PreReceiptRefundRequest::class.java)))
        else -> desk.status(read(params, StatusRequest::class.java).payCheckNumber.orEmpty())
    }

    private fun <T> read(params: JsonElement?, type: Class<T>): T {
        if (params == null || params.isJsonNull) throw failure("JSON_REQUIRED_FIELDS_NOT_FOUND")
        if (!params.isJsonObject) throw failure("JSON_WRONG_DATA_TYPE")
        return try {
            gson.fromJson(params, type)
        } catch (e: RuntimeException) {
            throw failure("JSON_WRONG_DATA_TYPE")
        }
    }

    private fun failure(name: String) = saleRules.failure(name)

    private data class StatusRequest(val payCheckNumber: String? = null)

    companion object {
        const val SALE = "sendPreReceipt"
        const val REFUND = "sendRefundPreReceipt"
        const val STATUS = "getPreReceiptStatus"
        val NAMES = setOf(SALE, REFUND, STATUS)
    }
}
