package uz.mobilkassa.kit.mock

import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockServerTest {

    private val server = MockServer(MockOptions(port = 0, idleSeconds = 10))
    private val port = server.start()
    private val http = HttpClient.newHttpClient()

    @AfterTest
    fun stop() = server.stop()

    private fun url(path: String) = URI.create("http://127.0.0.1:$port$path")

    private fun get(path: String) = http.send(HttpRequest.newBuilder(url(path)).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun post(path: String, body: String, contentType: String = "application/json") =
        http.send(post(path, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray(Charsets.UTF_8)), contentType), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))

    private fun post(path: String, publisher: HttpRequest.BodyPublisher, contentType: String = "application/json") =
        HttpRequest.newBuilder(url(path)).header("Content-Type", contentType).POST(publisher).build()

    private fun sale(n: String, key: String = "mock-kassa-key") =
        """{"id":"1","key":"$key","version":"1.0.2","method":"sendPreReceipt","params":{"payCheckNumber":"$n","type":"SALE","items":[
           {"name":"Чай чёрный","amount":1,"price":15000,"mxik":"02101001001000000","ownerType":0,"unit":"1","packageCode":1500,"taxPercent":12,"sku":"2050"}]}}"""

    private fun waitOpen(n: String) {
        val until = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < until) {
            val state = JsonParser.parseString(get("/mock/state").body()).asJsonObject
            if (state["open"]?.takeIf { it.isJsonObject }?.asJsonObject?.get("payCheckNumber")?.asString == n) return
            Thread.sleep(20)
        }
        error("$n never opened")
    }

    private val tooLarge = """{"id":null,"error":[{"code":-32600,"message":"Request too large"}]}"""

    @Test
    fun `health answers UP`() {
        assertEquals("""{"status":"UP"}""", get("/health").body())
    }

    @Test
    fun `body over 2 MB answers -32600`() {
        assertEquals(tooLarge, post("/pospoint", "x".repeat(2_097_153)).body())
    }

    @Test
    fun `chunked body over 2 MB answers -32600`() {
        val stream = HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(ByteArray(2_097_153) { 'x'.code.toByte() }) }
        assertEquals(tooLarge, http.send(post("/pospoint", stream), HttpResponse.BodyHandlers.ofString()).body())
    }

    @Test
    fun `control issue releases a held call`() {
        val call = http.sendAsync(post("/pospoint", HttpRequest.BodyPublishers.ofString(sale("A"))), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        waitOpen("A")
        assertEquals(200, post("/mock/issue", """{"payCheckNumber":"A"}""").statusCode())
        val result = JsonParser.parseString(call.get().body()).asJsonObject["result"].asJsonObject
        assertEquals("ISSUED", result["status"].asString)
    }

    @Test
    fun `control on unknown number answers 409`() {
        val answer = post("/mock/issue", """{"payCheckNumber":"NONE"}""")
        assertEquals(409, answer.statusCode())
        assertTrue(answer.body().contains("NONE"))
    }

    @Test
    fun `log hides the key`() {
        post("/pospoint", sale("A", key = "real-kassa-key-123"))
        val log = get("/mock/log").body()
        assertTrue(log.contains("sendPreReceipt"))
        assertFalse(log.contains("real-kassa-key-123"))
    }

    @Test
    fun `cyrillic name without charset comes back unchanged`() {
        val call = http.sendAsync(
            post("/pospoint", HttpRequest.BodyPublishers.ofByteArray(sale("A").toByteArray(Charsets.UTF_8)), "application/json"),
            HttpResponse.BodyHandlers.ofString(Charsets.UTF_8),
        )
        waitOpen("A")
        post("/mock/issue", """{"payCheckNumber":"A"}""")
        val item = JsonParser.parseString(call.get().body()).asJsonObject["result"].asJsonObject["items"].asJsonArray[0].asJsonObject
        assertEquals("Чай чёрный", item["name"].asString)
    }

    @Test
    fun `second server on a busy port throws PortBusy`() {
        val second = MockServer(MockOptions(port = port))
        assertEquals(port, assertFailsWith<PortBusy> { second.start() }.port)
    }

    @Test
    fun `cashier page is served`() {
        val page = get("/")
        assertEquals(200, page.statusCode())
        assertTrue(page.body().contains("Предчеки"))
    }

    @Test
    fun `seeded sale can be refunded`() {
        assertEquals(200, post("/mock/sales", """{"saleId":7,"items":[{"uid":11,"name":"Сахар","mxik":"1","barcode":"478","amount":2,"price":12000}]}""").statusCode())
        val refund = """{"id":"1","key":"mock-kassa-key","version":"1.0.2","method":"sendRefundPreReceipt","params":{"payCheckNumber":"R","saleId":7,"items":[{"barcode":"478","amount":1}]}}"""
        val call = http.sendAsync(post("/pospoint", HttpRequest.BodyPublishers.ofString(refund)), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        waitOpen("R")
        post("/mock/issue", """{"payCheckNumber":"R"}""")
        assertEquals(12000.0, JsonParser.parseString(call.get().body()).asJsonObject["result"].asJsonObject["total"].asDouble)
    }

    // A 1C server on another PC needs --host 0.0.0.0; the address shown must then be one it can reach.
    @Test
    fun `address shown for a LAN bind is not 0_0_0_0`() {
        val lan = MockServer(MockOptions(port = 0, host = "0.0.0.0"))
        lan.start()
        try {
            assertFalse(lan.address().contains("0.0.0.0"), lan.address())
            assertTrue(lan.address().endsWith("/pospoint"), lan.address())
        } finally {
            lan.stop()
        }
        assertEquals("http://127.0.0.1:$port/pospoint", server.address())
    }

    @Test
    fun `options parse`() {
        assertEquals(MockOptions(6470, "k", 5, "0.0.0.0"), MockOptions.parse(arrayOf("--port", "6470", "--key", "k", "--idle-seconds", "5", "--host", "0.0.0.0")))
        assertEquals(MockOptions(), MockOptions.parse(emptyArray()))
        assertFailsWith<IllegalArgumentException> { MockOptions.parse(arrayOf("--nope")) }
    }
}
