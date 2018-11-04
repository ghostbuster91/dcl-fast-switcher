package io.ghostbuster91.docker.compose.logger

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import de.gesellix.docker.compose.ComposeFileReader
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.UncheckedIOException
import java.util.*
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val workDir = System.getProperty("user.dir")
    val inputStream = FileInputStream(File(workDir, args.getOrElse(0) { "docker-compose.yml" }))
    val config = ComposeFileReader().loadYaml(inputStream)
    val services = config["services"]!!.keys.toList()
    val altMapping = mapOf("Ń" to 1, "™" to 2, "€" to 3, "ß" to 4, "į" to 5, "§" to 6, "¶" to 7, "•" to 8, "Ľ" to 9, "ľ" to 0)
    val shiftMapping = mapOf("!" to 1, "@" to 2, "#" to 3, "$" to 4, "%" to 5, "^" to 6, "&" to 7, "*" to 8, "(" to 9, ")" to 0)

    println("Services mapping:")
    services.forEachIndexed { index, s ->
        println("$index -> $s")
    }
    println("Press any key to continue...")
    DefaultTerminalFactory().createTerminal().use { terminal ->
        keyStrokeStream(terminal)
                .subscribeOn(Schedulers.io())
                .blockingFirst()
        val userCommand = createUserCommandStream(terminal, shiftMapping, services, altMapping)
        val servicesStream = userCommand
                .scan(ServiceStream(setOf(services[0]))) { acc: ServiceStream, item: UserCommand ->
                    when (item) {
                        is UserCommand.Add -> ServiceStream(acc.services + item.service)
                        is UserCommand.Single -> ServiceStream(setOf(item.service))
                        is UserCommand.Remove -> ServiceStream(acc.services - item.service)
                    }
                }
                .distinctUntilChanged()
                .doOnNext { println("Switched to $it") }

        servicesStream
                .switchMap { streamFromDockerCompose(it.services.toList()) }
                .blockingSubscribe { println(it) }
    }
}

private fun createUserCommandStream(terminal: Terminal, shiftMapping: Map<String, Int>, services: List<String>, altMapping: Map<String, Int>): Flowable<UserCommand> {
    val userInput = keyStrokeStream(terminal)
            .subscribeOn(Schedulers.io())
            .share()
    val shiftKey = userInput
            .filter { it.character.toString() in shiftMapping }
            .map { shiftMapping[it.character.toString()]!! }
            .map { UserCommand.Remove(services.getOrElse(it) { services[0] }) }
    val altKey = userInput
            .filter { it.character.toString() in altMapping }
            .map { altMapping[it.character.toString()]!! }
            .map { UserCommand.Add(services.getOrElse(it) { services[0] }) }
    val numberKey = userInput
            .filter { it.character.toString().matches("[0-9]".toRegex()) }
            .map { it.character.toString().toInt() }
            .map { UserCommand.Single(services.getOrElse(it) { services[0] }) }

    return Flowable.merge(shiftKey, altKey, numberKey)
}

private fun keyStrokeStream(terminal: Terminal): Flowable<KeyStroke> {
    return Flowable.interval(100, TimeUnit.MILLISECONDS)
            .map { Optional.ofNullable(terminal.pollInput()) }
            .filter { it.isPresent }
            .map { it.get() }
}

private fun streamFromDockerCompose(services: List<String>): Flowable<String> {
    return createProcess(listOf("docker-compose", "logs", "-f", "--tail=200") + services)
            .flatMap { process ->
                Flowable.using(
                        { process.inputStream.bufferedReader() },
                        { reader -> reader.toFlowable().subscribeOn(Schedulers.single()) },
                        { reader -> reader.close() })
                        .subscribeOn(Schedulers.io())
                        .doOnError { e -> e.printStackTrace() }
                        .onErrorResumeNext { t: Throwable -> if (t is UncheckedIOException) Flowable.empty<String>() else throw t }
                        .doOnCancel { process.destroy() }
                        .takeUntil { !process.isAlive }
                        .mergeWith(Flowable.never())
            }
}

private fun createProcess(command: Command): Flowable<Process> {
    return Flowable.create({ e: FlowableEmitter<Process> ->
        val process = ProcessBuilder()
                .directory(File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .command(command)
                .start()
        e.onNext(process)
        e.onComplete()
    }, BackpressureStrategy.DROP)
            .subscribeOn(Schedulers.io())
}

fun BufferedReader.toFlowable(): Flowable<String> {
    return Flowable.fromIterable(object : Iterable<String> {
        override fun iterator(): Iterator<String> {
            return lines().iterator()
        }
    })
}

typealias Command = List<String>

sealed class UserCommand {

    abstract val service: String

    data class Single(override val service: String) : UserCommand()
    data class Add(override val service: String) : UserCommand()
    data class Remove(override val service: String) : UserCommand()
}


data class ServiceStream(val services: Set<String>)
