package io.ghostbuster91.docker.compose.logger

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.Terminal
import io.ghostbuster91.docker.compose.logger.Colors.red
import io.ghostbuster91.docker.compose.logger.docker.readServicesFromDockerConfig
import io.ghostbuster91.docker.compose.logger.docker.streamFromDockerCompose
import io.ghostbuster91.docker.compose.logger.keyboard.EffectsMapping
import io.ghostbuster91.docker.compose.logger.keyboard.KeyboardLayout
import io.ghostbuster91.docker.compose.logger.keyboard.ServiceMappingImpl
import io.ghostbuster91.docker.compose.logger.keyboard.SingleServiceMapping
import io.ghostbuster91.docker.compose.logger.keyboard.linux.LinuxKeyboardLayout
import io.reactivex.Emitter
import io.reactivex.Flowable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

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

        keyStrokesStream
                .serviceStream(consoleOutConsumer, terminal, keyboardLayout)
                .switchMap { serviceDef ->
                    val stream = if (serviceDef.showHelp) {
                        emitHelp(keyboardLayout)
                                .concatWith(streamInfoHelp(serviceDef))
                                .concatWith(Flowable.never())
                    } else {
                        streamFromDockerCompose(serviceDef)
                    }
                    serviceDef.filter
                            ?.apply(stream)
                            ?.map { if (serviceDef.filter.text.isNotEmpty()) it.replace(serviceDef.filter.text, serviceDef.filter.text.red()) else it }
                            ?: stream
                }
                .blockingSubscribe(consoleOutConsumer)
    }
}

private fun Flowable<KeyStroke>.serviceStream(consoleOutConsumer: ConsoleOutConsumer, terminal: Terminal, keyboardLayout: LinuxKeyboardLayout): Flowable<StreamDefinition> {
    return doOnSubscribe { consoleOutConsumer.accept("Press any key to continue...") }
            .scan(StreamDefinition(setOf(terminal.waitForNumberInput(keyboardLayout).service))) { acc: StreamDefinition, keyStroke: KeyStroke ->
                val character = keyStroke.character?.toString()
                if (acc.filter != null) {
                    handleSearchInput(keyStroke, acc, character)
                } else {
                    handleUserCommand(keyboardLayout, keyStroke, acc)
                }
            }
            .distinctUntilChanged()
            .doOnNext { consoleOutConsumer.accept("Switched to $it") }
}

private fun handleSearchInput(keyStroke: KeyStroke, acc: StreamDefinition, character: String?): StreamDefinition {
    return when {
        keyStroke.keyType == KeyType.Escape -> acc.copy(filter = null)
        keyStroke.keyType == KeyType.Backspace -> acc.copy(filter = acc.filter!!.copy(text = acc.filter.text.dropLast(1)))
        character != null && keyStroke.keyType != KeyType.Enter -> acc.copy(filter = acc.filter!!.copy(text = acc.filter.text + character))
        else -> acc
    }
}

private fun handleUserCommand(keyboardLayout: LinuxKeyboardLayout, keyStroke: KeyStroke, acc: StreamDefinition): StreamDefinition {
    val userCommand = keyboardLayout.mapCommand(keyStroke)
    return when (userCommand) {
        is UserCommand.Add -> StreamDefinition(acc.services + userCommand.service)
        is UserCommand.Single -> StreamDefinition(setOf(userCommand.service))
        is UserCommand.Remove -> StreamDefinition(acc.services - userCommand.service)
        UserCommand.Control.SwitchTimestamp -> acc.copy(showTimeStamps = !acc.showTimeStamps)
        UserCommand.Control.SwitchHelp -> acc.copy(showHelp = !acc.showHelp)
        UserCommand.Control.SwitchSearchFor -> acc.copy(filter = Filter(""))
        else -> acc
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
        object SwitchSearchFor : Control()
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
                            val filter: Filter? = null)

data class Filter(val text: String)

private fun Filter.apply(input: Flowable<String>) = input.filter { it.contains(text) }