package uz.mobilkassa.kit.mock

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

// The cashier's hands: the page and the agent's tests call these instead of a person at the kassa.
class ControlRoutes(
    private val desk: Desk,
    private val book: SaleBook,
    private val log: ExchangeLog,
    private val info: () -> KassaInfo,
    private val gson: Gson,
    private val readBody: suspend ApplicationCall.() -> String,
) {

    data class KassaInfo(val address: String, val key: String, val idleSeconds: Long)

    private data class StateView(val info: KassaInfo, val open: Summary?, val parked: List<Summary>, val done: List<Summary>)

    private data class Action(val payCheckNumber: String? = null, val changes: Changes? = null)

    private data class SeedLine(
        val uid: Long? = null,
        val sku: String? = null,
        val name: String? = null,
        val mxik: String? = null,
        val barcode: String? = null,
        val amount: Double = 0.0,
        val price: Double = 0.0,
        val discount: Double = 0.0,
        val mkCode: List<String>? = null,
    )

    private data class Seed(val saleId: Long? = null, val items: List<SeedLine>? = null)

    fun install(route: Route) = with(route) {
        get("/mock/state") {
            val state = desk.state()
            call.json(gson.toJson(StateView(info(), state.open, state.parked, state.done)))
        }
        get("/mock/log") { call.json(gson.toJson(log.last())) }
        post("/mock/reset") {
            desk.reset()
            book.clear()
            log.clear()
            call.ok()
        }
        post("/mock/sales") {
            val seed = call.read<Seed>() ?: return@post
            val lines = seed.items.orEmpty().map {
                DraftLine(it.sku, it.name.orEmpty(), it.amount, it.price, it.discount, it.mxik, it.barcode, it.uid, it.mkCode.orEmpty())
            }
            if (seed.saleId == null || lines.isEmpty()) return@post call.conflict(HttpStatusCode.BadRequest, "Нужны saleId и items")
            book.seed(seed.saleId, lines)
            call.ok()
        }
        ACTIONS.forEach { name ->
            post("/mock/$name") {
                val action = call.read<Action>() ?: return@post
                val n = action.payCheckNumber ?: return@post call.conflict(HttpStatusCode.BadRequest, "Нет payCheckNumber")
                try {
                    when (name) {
                        "touch" -> desk.touch(n)
                        "park" -> desk.park(n)
                        "open" -> desk.open(n)
                        "issue" -> desk.issue(n, action.changes)
                        "reject" -> desk.reject(n)
                    }
                    call.ok()
                } catch (e: DeskConflict) {
                    call.conflict(HttpStatusCode.Conflict, e.message.orEmpty())
                }
            }
        }
    }

    private suspend inline fun <reified T> ApplicationCall.read(): T? = try {
        gson.fromJson(readBody(), T::class.java) ?: run { conflict(HttpStatusCode.BadRequest, "Пустое тело"); null }
    } catch (e: JsonSyntaxException) {
        conflict(HttpStatusCode.BadRequest, "Тело запроса не JSON")
        null
    }

    private suspend fun ApplicationCall.ok() = json("""{"ok":true}""")

    private suspend fun ApplicationCall.conflict(status: HttpStatusCode, message: String) =
        respondText(gson.toJson(mapOf("error" to message)), ContentType.Application.Json, status)

    private suspend fun ApplicationCall.json(text: String) = respondText(text, ContentType.Application.Json)

    companion object {
        val ACTIONS = listOf("touch", "park", "open", "issue", "reject")
    }
}
