package io.ghostbuster91.docker.compose.logger

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import de.gesellix.docker.compose.ComposeFileReader
import io.ghostbuster91.docker.compose.logger.Colors.red
import io.ghostbuster91.docker.compose.logger.keyboard.EffectsMapping
import io.ghostbuster91.docker.compose.logger.keyboard.KeyboardLayout
import io.ghostbuster91.docker.compose.logger.keyboard.ServiceMappingImpl
import io.ghostbuster91.docker.compose.logger.keyboard.SingleServiceMapping
import io.ghostbuster91.docker.compose.logger.keyboard.linux.LinuxKeyboardLayout
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.UncheckedIOException

typealias Service = String

fun main(args: Array<String>) {
    val services = readServicesFromDockerConfig(args)
    val consoleOutConsumer = ConsoleOutConsumer()
    val serviceMapping = ServiceMappingImpl(services)
    val keyboardLayout = LinuxKeyboardLayout(serviceMapping)
    emitHelp(keyboardLayout).blockingSubscribe(consoleOutConsumer)
    DefaultTerminalFactory().createTerminal().use { terminal ->
        val keyStrokesStream = keyStrokeStream(terminal)
                .subscribeOn(Schedulers.io())
                .share()
        userEffectsStream(keyStrokesStream, keyboardLayout)
                .subscribeOn(Schedulers.io())
                .subscribe {
                    when (it) {
                        is Effect.Println -> it.apply(consoleOutConsumer)
                    }
                }

        val servicesStream = keyStrokesStream
                .doOnSubscribe { consoleOutConsumer.accept("Press any key to continue...") }
                .scan(StreamDefinition(setOf(terminal.waitForNumberInput(keyboardLayout).service))) { acc: StreamDefinition, keyStroke: KeyStroke ->
                    val character = keyStroke.character?.toString()
                    if (acc.find != null) {
                        when {
                            keyStroke.keyType == KeyType.Escape -> acc.copy(find = null)
                            keyStroke.keyType == KeyType.Backspace -> acc.copy(find = acc.find.copy(text = acc.find.text.dropLast(1)))
                            character != null && keyStroke.keyType != KeyType.Enter-> acc.copy(find = acc.find.copy(text = acc.find.text + character))
                            else -> acc
                        }
                    } else {
                        val userCommand = keyboardLayout.mapCommand(keyStroke)
                        when (userCommand) {
                            is UserCommand.Add -> StreamDefinition(acc.services + userCommand.service)
                            is UserCommand.Single -> StreamDefinition(setOf(userCommand.service))
                            is UserCommand.Remove -> StreamDefinition(acc.services - userCommand.service)
                            UserCommand.Control.SwitchTimestamp -> acc.copy(showTimeStamps = !acc.showTimeStamps)
                            UserCommand.Control.SwitchHelp -> acc.copy(showHelp = !acc.showHelp)
                            UserCommand.Control.SwitchFind -> acc.copy(find = FindOption(""))
                            else -> acc
                        }
                    }
                }
                .distinctUntilChanged()
                .doOnNext { consoleOutConsumer.accept("Switched to $it") }

        servicesStream
                .switchMap { serviceDef ->
                    val stream = if (serviceDef.showHelp) {
                        emitHelp(keyboardLayout)
                                .concatWith(streamInfoHelp(serviceDef))
                                .concatWith(Flowable.never())
                    } else {
                        streamFromDockerCompose(serviceDef)
                    }
                    serviceDef.find
                            ?.filter(stream)
                            ?.map { if(serviceDef.find.text.isNotEmpty()) it.replace(serviceDef.find.text, serviceDef.find.text.red()) else it }
                            ?: stream
                }
                .blockingSubscribe(consoleOutConsumer)
    }
}

class ConsoleOutConsumer : Consumer<String> {
    override fun accept(t: String?) {
        println(t)
    }
}

private fun emitHelp(keyboardLayout: KeyboardLayout): Flowable<String> {
    return Flowable.fromIterable(listOf(
            "=======================================================",
            "Key bindings:") +
            keyboardLayout.getControlMapping() +
            listOf("Services mapping:") +
            keyboardLayout.getMapping().map { (letter, service) ->
                "$letter -> $service"
            })
}

private fun streamInfoHelp(streamDefinition: StreamDefinition): Flowable<String> {
    return Flowable.fromIterable(listOf("Stream info: ") + streamDefinition.services.joinToString())
}

private fun Terminal.waitForNumberInput(keyboardLayout: KeyboardLayout): UserCommand.Single {
    return keyStrokeStream(this)
            .subscribeOn(Schedulers.io())
            .let {
                userSingleStream(it, keyboardLayout)
            }
            .blockingFirst()
}

private fun readServicesFromDockerConfig(args: Array<String>): List<String> {
    val workDir = System.getProperty("user.dir")
    val inputStream = FileInputStream(File(workDir, args.getOrElse(0) { "docker-compose.yml" }))
    val config = ComposeFileReader().loadYaml(inputStream)
    return config["services"]!!.keys.toList()
}

private fun userSingleStream(userInput: Flowable<KeyStroke>, singleServiceMapping: SingleServiceMapping): Flowable<UserCommand.Single> {
    return userInput
            .filter { singleServiceMapping.isSingleServiceKey(it) }
            .map { singleServiceMapping.mapSingleServiceKey(it) }
}

private fun userEffectsStream(userInput: Flowable<KeyStroke>, effectMapping: EffectsMapping): Flowable<Effect> {
    return userInput
            .filter { effectMapping.isEffectKey(it) }
            .map { effectMapping.mapEffectKey(it) }
}

private fun keyStrokeStream(terminal: Terminal): Flowable<KeyStroke> {
    return Flowable.generate { e: Emitter<KeyStroke> ->
        e.onNext(terminal.readInput())
    }
}

private fun streamFromDockerCompose(streamDefinition: StreamDefinition): Flowable<String> {
    val command = DockerComposeCommandBuilder(
            services = streamDefinition.services.toList(),
            showTimestamps = streamDefinition.showTimeStamps)
            .build()
    return createProcess(command)
            .mergeWith(Flowable.never())
            .subscribeOn(Schedulers.io())
}

private fun createProcess(command: Command): Flowable<String> {
    return Flowable
            .using(
                    {
                        ProcessBuilder()
                                .directory(File(System.getProperty("user.dir")))
                                .redirectErrorStream(true)
                                .command(command)
                                .start()
                    },
                    { process -> process.inputStream.bufferedReader().toFlowable() },
                    { process -> process.destroy() }
            )
            .onErrorResumeNext { t: Throwable -> if (t is UncheckedIOException) Flowable.empty<String>() else throw t }

}

fun BufferedReader.toFlowable(): Flowable<String> {
    return Flowable.fromIterable(object : Iterable<String> {
        override fun iterator(): Iterator<String> {
            return lines().iterator()
        }
    })
}

typealias Command = List<String>

sealed class Action {
    object Unknown : Action()
}

sealed class UserCommand : Action() {
    data class Single(val service: String) : UserCommand()
    data class Add(val service: String) : UserCommand()
    data class Remove(val service: String) : UserCommand()
    sealed class Control : UserCommand() {
        object SwitchTimestamp : Control()
        object SwitchHelp : Control()
        object SwitchFind : Control()
    }
}

sealed class Effect : Action() {
    object Println : Effect() {
        fun apply(consumer: Consumer<String>) {
            consumer.accept("\n")
        }
    }
}


data class StreamDefinition(val services: Set<String>,
                            val showTimeStamps: Boolean = false,
                            val showHelp: Boolean = false,
                            val find: FindOption? = null)

data class FindOption(val text: String)

fun FindOption.filter(input: Flowable<String>) = input.filter { it.contains(text) }

data class DockerComposeCommandBuilder(val services: List<String>,
                                       val showTimestamps: Boolean = false,
                                       val showColors: Boolean = true,
                                       val follow: Boolean = true,
                                       val tailSize: Int = 200) {
    fun build(): Command {
        var command = listOf("docker-compose", "logs", "--tail=$tailSize")
        if (showTimestamps) {
            command += "--timestamps"
        }
        if (!showColors) {
            command += "--no-color"
        }
        if (follow) {
            command += "--follow"
        }
        return command + services
    }
}