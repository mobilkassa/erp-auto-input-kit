package uz.mobilkassa.kit.mock

data class MockOptions(
    val port: Int = 6461,
    val key: String = "mock-kassa-key",
    val idleSeconds: Long = 90,
    val host: String = "127.0.0.1",
) {
    companion object {
        const val USAGE = "mock-kassa [--port 6461] [--key mock-kassa-key] [--idle-seconds 90] [--host 127.0.0.1]"

        fun parse(args: Array<String>): MockOptions {
            var options = MockOptions()
            var i = 0
            while (i < args.size) {
                val value = args.getOrNull(i + 1) ?: throw IllegalArgumentException("Нет значения для ${args[i]}. $USAGE")
                options = when (args[i]) {
                    "--port" -> options.copy(port = value.toIntOrNull() ?: throw IllegalArgumentException("--port: нужно число"))
                    "--key" -> options.copy(key = value)
                    "--idle-seconds" -> options.copy(idleSeconds = value.toLongOrNull() ?: throw IllegalArgumentException("--idle-seconds: нужно число"))
                    "--host" -> options.copy(host = value)
                    else -> throw IllegalArgumentException("Неизвестный параметр ${args[i]}. $USAGE")
                }
                i += 2
            }
            return options
        }
    }
}
