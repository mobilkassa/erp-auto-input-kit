package uz.mobilkassa.kit.mock

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

// Every guide/examples/*.json against a fresh mock: the examples are the protocol the partners read.
// A cashier action targets the payCheckNumber of the latest request (setup or main).
class ExamplesReplayTest {

    private val http = HttpClient.newHttpClient()

    @TestFactory
    fun `guide examples replay against the mock`(): List<DynamicTest> {
        val dir = File(javaClass.getResource("/examples")!!.toURI())
        val files = dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        check(files.size >= 13) { "examples missing: ${files.size}" }
        return files.map { file -> DynamicTest.dynamicTest(file.nameWithoutExtension) { replay(JsonParser.parseString(file.readText()).asJsonObject) } }
    }

    private fun replay(example: JsonObject) {
        val server = MockServer(MockOptions(port = 0, idleSeconds = 1))
        val port = server.start()
        try {
            val run = Run(port)
            example.getAsJsonArray("setup")?.forEach { run.step(it.asJsonObject) }
            val answer = run.send(example.getAsJsonObject("request"))
            example.getAsJsonArray("cashier")?.forEach { run.act(it.asJsonObject) }
            assertEquals(example["answer"], JsonParser.parseString(answer.get().body()), example["title"].asString)
        } finally {
            server.stop()
        }
    }

    private inner class Run(private val port: Int) {
        private var latest: String? = null

        private fun url(path: String) = URI.create("http://127.0.0.1:$port$path")

        fun step(step: JsonObject) {
            step.getAsJsonObject("request")?.let { send(it) }
            step.getAsJsonArray("cashier")?.forEach { act(it.asJsonObject) }
            step.getAsJsonObject("seedSale")?.let { assertEquals(200, post("/mock/sales", it).statusCode()) }
        }

        fun send(request: JsonObject): CompletableFuture<HttpResponse<String>> {
            latest = request.getAsJsonObject("params")?.get("payCheckNumber")?.asString ?: latest
            return http.sendAsync(
                HttpRequest.newBuilder(url("/pospoint")).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(request.toString(), Charsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(Charsets.UTF_8),
            )
        }

        fun act(action: JsonObject) {
            val n = latest ?: error("no request to act on")
            val name = action["action"].asString
            if (name == "idle") return waitFor("$n leaves the window") { open() != n }
            waitFor("$n on the desk") { open() == n || n in parked() }
            val body = JsonObject().apply {
                addProperty("payCheckNumber", n)
                action["changes"]?.let { add("changes", it) }
            }
            val answer = post("/mock/$name", body)
            assertEquals(200, answer.statusCode(), answer.body())
        }

        private fun state(): JsonObject =
            JsonParser.parseString(http.send(HttpRequest.newBuilder(url("/mock/state")).build(), HttpResponse.BodyHandlers.ofString()).body()).asJsonObject

        private fun open(): String? = state()["open"]?.takeIf { it.isJsonObject }?.asJsonObject?.get("payCheckNumber")?.asString

        private fun parked(): List<String> = state().getAsJsonArray("parked").map { it.asJsonObject["payCheckNumber"].asString }

        private fun post(path: String, body: JsonElement) = http.send(
            HttpRequest.newBuilder(url(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), Charsets.UTF_8)).build(),
            HttpResponse.BodyHandlers.ofString(Charsets.UTF_8),
        )

        private fun waitFor(what: String, condition: () -> Boolean) {
            val until = System.currentTimeMillis() + 5000
            while (!condition()) {
                if (System.currentTimeMillis() > until) error("timed out waiting: $what")
                Thread.sleep(20)
            }
        }
    }
}
