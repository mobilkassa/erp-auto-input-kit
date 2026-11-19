package uz.mobilkassa.kit.mock

import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class Exchange(val time: String, val method: String?, val request: String, val answer: String)

// The last requests and answers for the cashier page. The "key" field is masked whatever it holds:
// an ERP may send its real kassa key to the mock.
class ExchangeLog(private val size: Int = 200) {

    private val lock = Any()
    private val items = ArrayDeque<Exchange>()

    fun add(request: String, answer: String) = synchronized(lock) {
        val method = METHOD.find(request)?.groupValues?.get(1)
        items.addFirst(Exchange(LocalTime.now().format(TIME), method, mask(request).take(MAX_TEXT), answer.take(MAX_TEXT)))
        while (items.size > size) items.removeLast()
    }

    fun last(): List<Exchange> = synchronized(lock) { items.toList() }

    fun clear() = synchronized(lock) { items.clear() }

    companion object {
        private const val MAX_TEXT = 4000
        private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
        private val KEY = Regex(""""key"\s*:\s*"(?:[^"\\]|\\.)*"""")
        private val METHOD = Regex(""""method"\s*:\s*"([^"]*)"""")

        fun mask(text: String) = KEY.replace(text, "\"key\":\"***\"")
    }
}
