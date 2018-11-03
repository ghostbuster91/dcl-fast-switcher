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
    val inputStream = FileInputStream(File(workDir, "docker-compose.yml"))
    val config = ComposeFileReader().loadYaml(inputStream)
    val services = config["services"]!!.keys.toList()
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

        val transformedUserInput = userInput
                .map { it.character.toString() }
                .filter { it.matches("[0-9]".toRegex()) }
                .map { it.toInt() }
                .startWith(0)
                .map { services.getOrElse(it) { services[0] } }
                .distinctUntilChanged()
                .doOnNext { println("Switched to $it") }

        transformedUserInput
                .switchMap { scriptStream(it) }
                .blockingSubscribe { println(it) }
    }
}

private fun keyStrokeStream(terminal: Terminal): Flowable<KeyStroke> {
    return Flowable.interval(100, TimeUnit.MILLISECONDS)
            .map { Optional.ofNullable(terminal.pollInput()) }
            .filter { it.isPresent }
            .map { it.get() }
}

private fun scriptStream(it: String): Flowable<String> {
    return createProcess(it)
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

private fun createProcess(it: String): Flowable<Process> {
    return Flowable.create({ e: FlowableEmitter<Process> ->
        val process = ProcessBuilder()
                .directory(File(System.getProperty("user.dir")))
                .redirectErrorStream(true)
                .command("docker-compose", "logs", "-f", "--tail=200", it)
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