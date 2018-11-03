package io.ghostbuster91.docker.compose.logger

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import de.gesellix.docker.compose.ComposeFileReader
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.UncheckedIOException
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    val workDir = System.getProperty("user.dir")
    val inputStream = FileInputStream(File(workDir, "docker-compose.yml"))
    val config = ComposeFileReader().loadYaml(inputStream)
    val services = config["services"]!!.keys.toList()
    println(services)

    DefaultTerminalFactory().createTerminal().use { terminal ->
        val userInput = keyStrokeStream(terminal)
                .subscribeOn(Schedulers.io())


        val transformedUserInput = userInput
                .map { it.character.toString() }
                .filter { it.matches("[0-9]".toRegex()) }
                .map { it.toInt() }
                .startWith(0)
                .map { services.getOrElse(it) { services[0] } }
                .doOnNext { println("Switched to $it") }
        scriptStream()
                .withLatestFrom(transformedUserInput, BiFunction<String, String, Pair<String, String>> { t1, t2 -> t1 to t2 })
                .filter { (line,service)-> line.take(service.length * 2).contains(service)}
                .blockingSubscribe { println(it.first) }
    }
}

private fun keyStrokeStream(terminal: Terminal): Flowable<KeyStroke> {
    return Flowable.create({ e: FlowableEmitter<KeyStroke> ->
        val stopper = terminal.addKeyStrokeListener { e.onNext(it) }
        e.setCancellable { stopper.set(false) }
    }, BackpressureStrategy.MISSING)
}

private fun Terminal.addKeyStrokeListener(onKeyStroke: (KeyStroke) -> Unit): AtomicBoolean {
    val isRunning = AtomicBoolean(true)
    while (isRunning.get()) {
        val pollInput = pollInput()
        if (pollInput != null && pollInput.keyType == KeyType.Character) {
            println(pollInput)
            onKeyStroke(pollInput)
        }
        Thread.sleep(100)
    }
    return isRunning
}


private fun scriptStream(): Flowable<String> {
    return createProcess()
            .flatMap { process ->
                Flowable.using(
                        { process.inputStream.bufferedReader() },
                        { reader -> wrapInIterable(reader).subscribeOn(Schedulers.single()) },
                        { reader -> reader.close() })
                        .subscribeOn(Schedulers.io())
                        .doOnError { e -> e.printStackTrace() }
                        .onErrorResumeNext { t: Throwable -> if (t is UncheckedIOException) Flowable.empty<String>() else throw t }
            }
}

private fun createProcess(): Flowable<Process> {
    return Flowable.create({ e: FlowableEmitter<Process> ->
        val process = Runtime.getRuntime().exec("docker-compose logs -f")
        e.onNext(process)
        e.setCancellable {
            process.destroy()
        }
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