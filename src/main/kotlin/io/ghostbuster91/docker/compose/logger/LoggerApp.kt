package io.ghostbuster91.docker.compose.logger

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import de.gesellix.docker.compose.ComposeFileReader
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.UncheckedIOException
import java.util.*
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val services = readServicesFromDockerConfig(args)
    println("Services mapping:")
    services.forEachIndexed { index, s ->
        println("$index -> $s")
    }
    println("Press any key to continue...")
    DefaultTerminalFactory().createTerminal().use { terminal ->
        val servicesStream = createUserCommandStream(terminal, services)
                .scan(StreamDefinition(setOf(terminal.waitForNumberInput(services).service))) { acc: StreamDefinition, item: UserCommand ->
                    when (item) {
                        is UserCommand.Add -> StreamDefinition(acc.services + item.service)
                        is UserCommand.Single -> StreamDefinition(setOf(item.service))
                        is UserCommand.Remove -> StreamDefinition(acc.services - item.service)
                        UserCommand.SwitchTimestamp -> acc.copy(showTimeStamps = !acc.showTimeStamps)
                    }
                }
                .distinctUntilChanged()
                .doOnNext { println("Switched to $it") }

        servicesStream
                .switchMap { streamFromDockerCompose(it) }
                .blockingSubscribe { println(it) }
    }
}

private fun Terminal.waitForNumberInput(services: List<String>): UserCommand.Single {
    return keyStrokeStream(this)
            .subscribeOn(Schedulers.io())
            .let {
                userNumberInput(it, services)
            }
            .blockingFirst()
}

private fun readServicesFromDockerConfig(args: Array<String>): List<String> {
    val workDir = System.getProperty("user.dir")
    val inputStream = FileInputStream(File(workDir, args.getOrElse(0) { "docker-compose.yml" }))
    val config = ComposeFileReader().loadYaml(inputStream)
    return config["services"]!!.keys.toList()
}

private fun createUserCommandStream(terminal: Terminal, services: List<String>): Flowable<UserCommand> {
    val userInput = keyStrokeStream(terminal)
            .subscribeOn(Schedulers.io())
            .share()
    val shiftKey = userShiftInput(userInput, services)
    val altKey = userAltInput(userInput, services)
    val numberKey = userNumberInput(userInput, services)
    val optionsKey = userOptionInput(userInput)
    return Flowable.merge(shiftKey, altKey, numberKey, optionsKey)
}

private fun userShiftInput(userInput: Flowable<KeyStroke>, services: List<String>): Flowable<UserCommand.Remove> {
    val shiftMapping = mapOf("!" to 1, "@" to 2, "#" to 3, "$" to 4, "%" to 5, "^" to 6, "&" to 7, "*" to 8, "(" to 9, ")" to 0)
    return userInput
            .filter { it.character?.toString() in shiftMapping }
            .map { shiftMapping[it.character.toString()]!! }
            .map { UserCommand.Remove(services.getOrElse(it) { services[0] }) }
}

private fun userNumberInput(userInput: Flowable<KeyStroke>, services: List<String>): Flowable<UserCommand.Single> {
    return userInput
            .filter { it.character?.toString()?.matches("[0-9]".toRegex()) ?: false }
            .map { it.character.toString().toInt() }
            .map { UserCommand.Single(services.getOrElse(it) { services[0] }) }
}

private fun userAltInput(userInput: Flowable<KeyStroke>, services: List<String>): Flowable<UserCommand.Add> {
    val altMapping = mapOf("Ń" to 1, "™" to 2, "€" to 3, "ß" to 4, "į" to 5, "§" to 6, "¶" to 7, "•" to 8, "Ľ" to 9, "ľ" to 0)
    return userInput
            .filter { it.character?.toString() in altMapping }
            .map { altMapping[it.character.toString()]!! }
            .map { UserCommand.Add(services.getOrElse(it) { services[0] }) }
}

private fun userOptionInput(userInput: Flowable<KeyStroke>): Flowable<UserCommand.SwitchTimestamp> {
    val optionMapping = mapOf("t" to UserCommand.SwitchTimestamp)
    return userInput
            .filter { it.character?.toString() in optionMapping }
            .map { optionMapping[it.character.toString()]!! }
}

private fun keyStrokeStream(terminal: Terminal): Flowable<KeyStroke> {
    return Flowable.interval(100, TimeUnit.MILLISECONDS)
            .map { Optional.ofNullable(terminal.pollInput()) }
            .filter { it.isPresent }
            .map { it.get() }
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

sealed class UserCommand {
    data class Single(val service: String) : UserCommand()
    data class Add(val service: String) : UserCommand()
    data class Remove(val service: String) : UserCommand()
    object SwitchTimestamp : UserCommand()
}


data class StreamDefinition(val services: Set<String>,
                            val showTimeStamps: Boolean = false)

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