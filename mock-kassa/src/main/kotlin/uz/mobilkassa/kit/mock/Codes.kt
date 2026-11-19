package uz.mobilkassa.kit.mock

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// `from`: the kassa file that sends this code with its own message (not ErrorCode.kt defaults), e.g. PaymentErrorRpc.kt.
data class CodeEntry(val code: Int, val name: String, val message: String, val ru: String, val from: String? = null)

// The codes come from guide/error-codes.json, the table the guide prints; no message is typed in code.
class Codes(entries: List<CodeEntry>) {

    private val byName = entries.associateBy { it.name }

    fun error(name: String): RpcError =
        byName[name]?.let { RpcError(it.code, it.message) } ?: throw IllegalArgumentException("Unknown code name $name")

    fun failure(vararg names: String) = RpcFailure(names.map { error(it) })

    companion object {
        fun load(): Codes {
            val text = Codes::class.java.getResource("/error-codes.json")?.readText(Charsets.UTF_8)
                ?: throw IllegalStateException("error-codes.json is not on the classpath")
            return Codes(Gson().fromJson(text, object : TypeToken<List<CodeEntry>>() {}.type))
        }
    }
}
