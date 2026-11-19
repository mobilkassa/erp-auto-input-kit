package uz.mobilkassa.kit.mock

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class MockMethodsTest {

    private val codes = Codes.load()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val book = SaleBook()
    private val desk = Desk(10.seconds, codes, scope, book::record)
    private val methods = MockMethods(Gson(), SaleRules(codes), RefundRules(codes), RefundMatcher(book, codes, desk::pending), desk)
    private val endpoint = RpcEndpoint("mock-kassa-key", codes, methods)

    private fun call(method: String, params: String?): Deferred<JsonElement> = scope.async {
        val p = params?.let { ""","params":$it""" } ?: ""
        JsonParser.parseString(endpoint.handle("""{"id":"1","key":"mock-kassa-key","version":"1.0.2","method":"$method"$p}"""))
    }

    private suspend fun issueWhenOpen(n: String) {
        withTimeout(2000) { while (desk.state().open?.payCheckNumber != n) delay(10) }
        desk.issue(n, null)
    }

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    private val tea = """{"name":"Чай","amount":2,"price":15000,"mxik":"02101001001000000","ownerType":0,"unit":"1","packageCode":1500,"taxPercent":12,"sku":"2050"}"""

    @Test
    fun `a sale is issued through the endpoint`() = runBlocking {
        val answer = call("sendPreReceipt", """{"payCheckNumber":"A","type":"SALE","items":[$tea],"byCash":30000}""")
        issueWhenOpen("A")
        assertEquals(json("""{"payCheckNumber":"A","status":"ISSUED","type":"SALE","changed":false,
            "items":[{"sku":"2050","name":"Чай","amount":2,"price":15000,"discount":0,"sum":30000}],
            "payments":[{"type":"CASH","amount":30000}],"total":30000}"""), answer.await().asJsonObject["result"])
    }

    @Test
    fun `numbers as strings are accepted`() = runBlocking {
        val asText = tea.replace("\"amount\":2", "\"amount\":\"2\"").replace("\"price\":15000", "\"price\":\"15000\"")
        val answer = call("sendPreReceipt", """{"payCheckNumber":"A","type":"SALE","items":[$asText],"byCash":"30000"}""")
        issueWhenOpen("A")
        assertEquals(30000.0, answer.await().asJsonObject["result"].asJsonObject["total"].asDouble)
    }

    @Test
    fun `a value Gson cannot read answers 14803`() = runBlocking {
        val bad = tea.replace("\"amount\":2", "\"amount\":\"two\"")
        assertEquals(json("""{"id":0,"error":[{"code":14803,"message":"JSON_WRONG_DATA_TYPE"}]}"""), call("sendPreReceipt", """{"payCheckNumber":"A","type":"SALE","items":[$bad]}""").await())
    }

    @Test
    fun `missing params answers 14804`() = runBlocking {
        assertEquals(json("""{"id":0,"error":[{"code":14804,"message":"JSON_REQUIRED_FIELDS_NOT_FOUND"}]}"""), call("sendPreReceipt", null).await())
    }

    @Test
    fun `status without number answers 14804`() = runBlocking {
        assertEquals(json("""{"id":0,"error":[{"code":14804,"message":"JSON_REQUIRED_FIELDS_NOT_FOUND"}]}"""), call("getPreReceiptStatus", "{}").await())
    }

    @Test
    fun `status and refund through the endpoint`() = runBlocking {
        val sale = call("sendPreReceipt", """{"payCheckNumber":"A","type":"SALE","items":[$tea]}""")
        issueWhenOpen("A")
        sale.await()
        assertEquals("ISSUED", call("getPreReceiptStatus", """{"payCheckNumber":"A"}""").await().asJsonObject["result"].asJsonObject["status"].asString)
        val refund = call("sendRefundPreReceipt", """{"payCheckNumber":"R","salePayCheckNumber":"A","items":[{"sku":"2050","amount":1}]}""")
        issueWhenOpen("R")
        assertEquals("REFUND", refund.await().asJsonObject["result"].asJsonObject["type"].asString)
    }

    @Test
    fun `refund rules answer id 0, matching errors the request id`() = runBlocking {
        assertEquals(0, call("sendRefundPreReceipt", """{"salePayCheckNumber":"A"}""").await().asJsonObject["id"].asInt)
        assertEquals(json("""{"id":1,"error":[{"code":14829,"message":"PRERECEIPT_NOT_FOUND"}]}"""), call("sendRefundPreReceipt", """{"payCheckNumber":"R","salePayCheckNumber":"NONE"}""").await())
    }
}
