package uz.mobilkassa.kit.mock

import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentCharset
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import java.net.BindException
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.time.Duration.Companion.seconds

class PortBusy(val port: Int, cause: Throwable) : Exception("Port $port is busy", cause)

// The mock kassa: POST /pospoint as the kassa's 6461 server, /health, the cashier page and /mock/* control.
class MockServer(private val options: MockOptions) {

    private val gson = Gson()
    private val codes = Codes.load()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val book = SaleBook()
    private val desk = Desk(options.idleSeconds.seconds, codes, scope, book::record)
    private val endpoint = RpcEndpoint(
        options.key, codes, MockMethods(gson, SaleRules(codes), RefundRules(codes), RefundMatcher(book, codes, desk::pending), desk), gson,
    )
    private val log = ExchangeLog()
    private var server: EmbeddedServer<*, *>? = null
    private var boundPort = options.port

    // Returns the bound port (port 0 picks a free one).
    fun start(): Int {
        val engine = embeddedServer(CIO, port = options.port, host = options.host) {
            routing {
                post("/pospoint") { pospoint(call) }
                get("/health") { call.respondText("""{"status":"UP"}""", ContentType.Application.Json) }
                get("/") { call.respondText(page(), ContentType.Text.Html) }
                ControlRoutes(desk, book, log, ::info, gson) { body() }.install(this)
            }
        }
        try {
            engine.start(wait = false)
        } catch (e: Throwable) {
            engine.stop(0, 0)
            if (generateSequence(e) { it.cause }.any { it is BindException }) throw PortBusy(options.port, e)
            throw e
        }
        server = engine
        boundPort = runBlocking { engine.engine.resolvedConnectors().first().port }
        return boundPort
    }

    fun stop() {
        server?.stop(100, 500)
        server = null
        scope.cancel()
    }

    private suspend fun pospoint(call: ApplicationCall) {
        val declared = call.request.contentLength() ?: 0
        val body = if (declared > MAX_BODY) null else call.body(MAX_BODY)
        val answer = if (body == null) tooLarge() else endpoint.handle(body)
        log.add(body ?: "<больше $MAX_BODY байт>", answer)
        call.respondText(answer, ContentType.Application.Json)
    }

    // The kassa's literal answer to an oversized body, the one answer with "id":null.
    private fun tooLarge(): String {
        val error = codes.error("REQUEST_TOO_LARGE")
        return """{"id":null,"error":[{"code":${error.code},"message":"${error.message}"}]}"""
    }

    // UTF-8 unless the request names another charset; null when the body is over the limit (chunked bodies too).
    private suspend fun ApplicationCall.body(limit: Long = MAX_BODY): String? {
        val bytes = receiveChannel().readRemaining(limit + 1).readByteArray()
        if (bytes.size > limit) return null
        return String(bytes, request.contentCharset() ?: Charsets.UTF_8)
    }

    private suspend fun ApplicationCall.body(): String = body(MAX_BODY).orEmpty()

    // The address an ERP puts in its settings. A LAN bind (0.0.0.0) shows this PC's LAN address: a 1C server on
    // another machine runs the module, and 127.0.0.1 would point it at itself.
    fun address(): String {
        val host = if (options.host == ANY_HOST) lanAddress() ?: "<IP этого компьютера>" else options.host
        return "http://$host:$boundPort/pospoint"
    }

    private fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    private fun info() = ControlRoutes.KassaInfo(address(), options.key, options.idleSeconds)

    private fun page(): String = MockServer::class.java.getResource("/cashier.html")!!.readText(Charsets.UTF_8)

    companion object {
        const val MAX_BODY = 2_097_152L
        const val ANY_HOST = "0.0.0.0"
    }
}
