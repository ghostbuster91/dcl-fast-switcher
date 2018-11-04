package io.ghostbuster91.docker.compose.logger

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import de.gesellix.docker.compose.ComposeFileReader
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.UncheckedIOException
import java.util.*
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val workDir = System.getProperty("user.dir")
    val inputStream = FileInputStream(File(workDir, "docker-compose.yml"))
    val config = ComposeFileReader().loadYaml(inputStream)
    val services = config["services"]!!.keys.toList()
    val altMapping = mapOf("Ń" to 1, "™" to 2, "€" to 3, "ß" to 4, "į" to 5, "§" to 6, "¶" to 7, "•" to 8, "Ľ" to 9, "ľ" to 0)
    println("Services mapping:")
    services.forEachIndexed { index, s ->
        println("$index -> $s")
    }
    println("Press any key to continue...")
    DefaultTerminalFactory().createTerminal().use { terminal ->
        keyStrokeStream(terminal)
                .subscribeOn(Schedulers.io())
                .blockingFirst()
        val userInput = keyStrokeStream(terminal)
                .subscribeOn(Schedulers.io())
                .filter { it.character.toString().matches("[0-9]".toRegex()) || it.character.toString() in altMapping.keys }
        val userCommand = userInput
                .map {
                    val isAltDown = it.character.toString() in altMapping.keys
                    val index = if (isAltDown) {
                        altMapping[it.character.toString()]!!
                    } else {
                        it.character.toString().toInt()
                    }
                    val service = services.getOrElse(index) { services[0] }
                    if (isAltDown) {
                        UserCommand.Add(service)
                    } else {
                        UserCommand.ShowSingle(service)
                    }
                }

        val transformedUserInput = userCommand
                .distinctUntilChanged(Function<UserCommand, String> { it.service })
                .scan(StreamType.Single(services[0]) as StreamType) { acc: StreamType, item: UserCommand ->
                    when (item) {
                        is UserCommand.Add -> StreamType.Multiple(acc.services + item.service)
                        is UserCommand.ShowSingle -> StreamType.Single(item.service)
                    }
                }
                .doOnNext { println("Switched to $it") }

        transformedUserInput
                .switchMap { scriptStream(it.services) }
                .blockingSubscribe { println(it) }
    }
}

private fun keyStrokeStream(terminal: Terminal): Flowable<KeyStroke> {
    return Flowable.interval(100, TimeUnit.MILLISECONDS)
            .map { Optional.ofNullable(terminal.pollInput()) }
            .filter { it.isPresent }
            .map { it.get() }
}

private fun scriptStream(services: List<String>): Flowable<String> {
    return createProcess(listOf("docker-compose", "logs", "-f", "--tail=2") + services)
            .flatMap { process ->
                Flowable.using(
                        { process.inputStream.bufferedReader() },
                        { reader -> wrapInIterable(reader).subscribeOn(Schedulers.single()) },
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

fun wrapInIterable(inputStream: BufferedReader): Flowable<String> {
    return Flowable.fromIterable(object : Iterable<String> {
        override fun iterator(): Iterator<String> {
            return inputStream.lines().iterator()
        }
    })
}

typealias Command = List<String>

sealed class UserCommand {

    abstract val service: String

    data class ShowSingle(override val service: String) : UserCommand()
    data class Add(override val service: String) : UserCommand()
}

sealed class StreamType {
    abstract val services: List<String>

    data class Single(val service: String) : StreamType() {
        override val services = listOf(service)
    }

    data class Multiple(override val services: List<String>) : StreamType()
}
