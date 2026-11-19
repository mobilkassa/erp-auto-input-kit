package uz.mobilkassa.kit.mock

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// Keeps guide/error-codes.json equal to the kassa's ErrorCode enum. Runs only with POSPOINT_ERRORCODE_KT set:
// git -C ../../MobilKassa show dev/v1.0.592:core/external/src/commonMain/kotlin/uz/newtech/pospoint/external/dto/ErrorCode.kt > build/ErrorCode.kt
class CodesMatchKassaTest {

    private val entry = Regex("""^\s*([A-Z_0-9]+)\(\s*(0x[0-9A-Fa-f]+|-?\d+)\s*(?:,\s*"([^"]*)")?""", RegexOption.MULTILINE)

    @Test
    fun `every positive code matches ErrorCode kt`() {
        val path = System.getenv("POSPOINT_ERRORCODE_KT")
        assumeTrue(path != null, "POSPOINT_ERRORCODE_KT is not set")
        val kassa = entry.findAll(File(path!!).readText()).associate { match ->
            val (name, code, message) = match.destructured
            val number = if (code.startsWith("0x")) code.drop(2).toInt(16) else code.toInt()
            name to (number to message.ifBlank { name })
        }
        val text = javaClass.getResource("/error-codes.json")!!.readText()
        val table: List<CodeEntry> = Gson().fromJson(text, object : TypeToken<List<CodeEntry>>() {}.type)
        table.filter { it.code > 0 }.forEach {
            val found = assertNotNull(kassa[it.name], "${it.name} is not in ErrorCode.kt")
            assertEquals(found.first, it.code, it.name)
            // An entry with `from` is sent by that file with its own message; only its code must match the enum.
            if (it.from == null) assertEquals(found.second, it.message, it.name)
        }
    }
}
