package uz.mobilkassa.kit.mock

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val options = try {
        MockOptions.parse(args)
    } catch (e: IllegalArgumentException) {
        System.err.println(e.message)
        exitProcess(2)
    }
    val server = MockServer(options)
    val port = try {
        server.start()
    } catch (e: PortBusy) {
        println("Порт ${e.port} занят: закройте Pospoint или запустите с --port ${e.port + 1}")
        exitProcess(2)
    }
    println("Тестовая касса Mobilkassa, протокол 1.0.2")
    println("Адрес для учётной системы: ${server.address()}")
    println("Ключ: ${options.key}")
    println("Экран кассира: http://127.0.0.1:$port/")
    if (options.host == "127.0.0.1")
        println("Учётная система на другом компьютере (сервер 1С): запустите с --host 0.0.0.0")
    Thread.currentThread().join()
}
