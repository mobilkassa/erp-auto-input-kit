package uz.mobilkassa.kit.mock

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RpcEndpointTest {

    private val codes = Codes.load()

    // sendPreReceipt: params without "bad" pass validation; "fail" fails inside the method.
    private val methods = object : AutoInputMethods {
        override fun has(method: String) = method == "sendPreReceipt"
        override fun validate(method: String, params: JsonElement?) {
            if (params?.asJsonObject?.has("bad") == true) throw codes.failure("JSON_REQUIRED_FIELDS_NOT_FOUND")
        }
        override suspend fun call(method: String, params: JsonElement?): Any {
            if (params?.asJsonObject?.has("fail") == true) throw codes.failure("PRERECEIPT_WAITING")
            return mapOf("ok" to true)
        }
    }

    private val endpoint = RpcEndpoint("mock-kassa-key", codes, methods)

    private fun ask(body: String): JsonElement = JsonParser.parseString(runBlocking { endpoint.handle(body) })

    private fun request(method: String = "sendPreReceipt", key: String = "mock-kassa-key", id: String = "\"1\"", version: String = "\"1.0.2\"", params: String = "{}") =
        """{"id":$id,"key":"$key","version":$version,"method":"$method","params":$params}"""

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    @Test
    fun `broken JSON answers parse error without id`() {
        assertEquals(json("""{"error":[{"code":-32700,"message":"Parse error"}]}"""), ask("{not json"))
    }

    @Test
    fun `missing version field answers parse error`() {
        assertEquals(json("""{"error":[{"code":-32700,"message":"Parse error"}]}"""), ask("""{"id":"1","key":"mock-kassa-key","method":"sendPreReceipt"}"""))
    }

    @Test
    fun `wrong key answers 14800 before anything else`() {
        assertEquals(json("""{"id":0,"error":[{"code":14800,"message":"UNAUTHORIZED"}]}"""), ask(request(method = "noSuchMethod", key = "wrong")))
    }

    @Test
    fun `id that is not a number answers 14822`() {
        assertEquals(json("""{"id":0,"error":[{"code":14822,"message":"WRONG_DATA_TYPE"}]}"""), ask(request(id = "\"abc\"")))
    }

    @Test
    fun `empty version answers -32602`() {
        assertEquals(
            json("""{"id":0,"error":[{"code":-32602,"message":"Invalid params (For input string: \"\")"}]}"""),
            ask(request(version = "\"\"")),
        )
    }

    @Test
    fun `version with two parts answers internal error`() {
        assertEquals(json("""{"error":[{"code":-32603,"message":"Internal error"}]}"""), ask(request(version = "\"1.0\"")))
    }

    @Test
    fun `version 1_1_0 answers 14801`() {
        assertEquals(json("""{"id":0,"error":[{"code":14801,"message":"VERSION_INCOMPATIBLE"}]}"""), ask(request(version = "\"1.1.0\"")))
    }

    @Test
    fun `version 1_0_1 is accepted`() {
        assertEquals(json("""{"id":1,"result":{"ok":true}}"""), ask(request(version = "\"1.0.1\"")))
    }

    @Test
    fun `the six old methods answer -32601 Method not found`() {
        listOf("registerSale", "registerSaleFull", "registerRefund", "registerRefundFull", "getReceipt", "getReceiptByPayCheckNumber").forEach {
            assertEquals(json("""{"id":1,"error":[{"code":-32601,"message":"Method not found"}]}"""), ask(request(method = it)), it)
        }
    }

    @Test
    fun `an FM method answers -32601 with the mock note`() {
        assertEquals(
            json("""{"id":1,"error":[{"code":-32601,"message":"Not in the mock kassa: test this method on a real kassa"}]}"""),
            ask(request(method = "getXReport")),
        )
    }

    @Test
    fun `an unknown method answers -32601`() {
        assertEquals(json("""{"id":1,"error":[{"code":-32601,"message":"Method not found"}]}"""), ask(request(method = "noSuchMethod")))
    }

    @Test
    fun `result echoes id as a number`() {
        assertEquals(json("""{"id":1,"result":{"ok":true}}"""), ask(request()))
    }

    @Test
    fun `a numeric id is accepted`() {
        assertEquals(json("""{"id":5,"result":{"ok":true}}"""), ask(request(id = "5")))
    }

    @Test
    fun `validation errors answer id 0`() {
        assertEquals(json("""{"id":0,"error":[{"code":14804,"message":"JSON_REQUIRED_FIELDS_NOT_FOUND"}]}"""), ask(request(params = """{"bad":1}""")))
    }

    @Test
    fun `method errors answer with the request id`() {
        assertEquals(json("""{"id":1,"error":[{"code":14827,"message":"PRERECEIPT_WAITING"}]}"""), ask(request(params = """{"fail":1}""")))
    }
}
