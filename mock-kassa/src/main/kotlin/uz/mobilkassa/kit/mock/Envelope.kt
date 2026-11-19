package uz.mobilkassa.kit.mock

import com.google.gson.JsonElement

data class RpcError(val code: Int, val message: String)

// Thrown by rules and the desk; the endpoint turns it into an error array.
class RpcFailure(val errors: List<RpcError>) : Exception(errors.joinToString { it.code.toString() })

// The auto-input methods behind the endpoint. A failure in validate answers "id":0, as the kassa reads the
// request id only after its checks; a failure in call answers with the request id.
interface AutoInputMethods {
    fun has(method: String): Boolean
    fun validate(method: String, params: JsonElement?)
    suspend fun call(method: String, params: JsonElement?): Any
}

// The kassa's answer: plain Gson drops null fields, so an answer without an id has no "id".
internal data class Answer(val id: Long?, val result: Any? = null, val error: List<RpcError>? = null)
