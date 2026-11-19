package uz.mobilkassa.kit.mock

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

// One request body in, one answer body out, in the kassa's order of checks
// (GsonRpcSerializer.validate, MessageValidatorImpl, KtorServerPeer on pospoint dev/v1.0.592).
class RpcEndpoint(
    private val key: String,
    private val codes: Codes,
    private val methods: AutoInputMethods,
    private val gson: Gson = Gson(),
) {

    suspend fun handle(body: String): String {
        val request = parse(body) ?: return answer(null, codes.error("PARSE_ERROR"))
        val method = request.get("method").asString
        val rawId = runCatching { request.get("id").asString }.getOrNull()
        val version = runCatching { request.get("version").asString }.getOrNull()
        if (rawId == null || version == null || runCatching { request.get("key").asString }.getOrNull() == null)
            return answer(null, codes.error("INTERNAL_ERROR"))
        val params = request.get("params")

        if (request.get("key").asString != key) return answer(0, codes.error("UNAUTHORIZED"))
        val id = rawId.toLongOrNull() ?: return answer(0, codes.error("WRONG_DATA_TYPE"))
        val parts = try {
            version.split('.').map { it.toInt() }
        } catch (e: NumberFormatException) {
            return answer(0, RpcError(codes.error("INVALID_PARAMS").code, "Invalid params (${e.message})"))
        }
        if (parts.size < 3) return answer(null, codes.error("INTERNAL_ERROR"))
        if (!(parts[0] <= PROTOCOL[0] && parts[1] <= PROTOCOL[1] && parts[2] <= PROTOCOL[2]))
            return answer(0, codes.error("VERSION_INCOMPATIBLE"))

        if (methods.has(method)) {
            try {
                methods.validate(method, params)
            } catch (e: RpcFailure) {
                return answer(0, e.errors)
            }
        }

        return when {
            method in OLD_RECEIPT_METHODS -> answer(id, codes.error("METHOD_NOT_FOUND"))
            method in REAL_KASSA_METHODS -> answer(id, RpcError(codes.error("METHOD_NOT_FOUND").code, NOT_IN_MOCK))
            !methods.has(method) -> answer(id, codes.error("METHOD_NOT_FOUND"))
            else -> try {
                gson.toJson(Answer(id, result = methods.call(method, params)))
            } catch (e: RpcFailure) {
                answer(id, e.errors)
            }
        }
    }

    // As GsonRpcSerializer.validate: a JSON object with method, key, id and version, else a parse error.
    private fun parse(body: String): JsonObject? = runCatching {
        JsonParser.parseString(body).asJsonObject.takeIf {
            it.get("method")?.asString != null && it.has("key") && it.has("id") && it.has("version")
        }
    }.getOrNull()

    private fun answer(id: Long?, error: RpcError) = answer(id, listOf(error))

    private fun answer(id: Long?, errors: List<RpcError>): String = gson.toJson(Answer(id, error = errors))

    companion object {
        val PROTOCOL = listOf(1, 0, 2)

        val OLD_RECEIPT_METHODS = setOf(
            "registerSale", "registerSaleFull", "registerRefund", "registerRefundFull", "getReceipt", "getReceiptByPayCheckNumber",
        )

        // Methods a real kassa answers in auto-input mode; the mock has no FM behind it.
        val REAL_KASSA_METHODS = setOf(
            "checkFmStatus", "checkFmCapacity", "getFmInfo", "syncFmState", "syncWithOfd", "openZReport", "getXReport",
            "closeZReport", "validateMarkCode", "validateReceiptMarkCodes", "validateMxikPackages", "getMxikInfo",
        )

        const val NOT_IN_MOCK = "Not in the mock kassa: test this method on a real kassa"
    }
}
