package io.ghostbuster91.docker.compose.logger.keyboard

import com.googlecode.lanterna.input.KeyStroke
import io.ghostbuster91.docker.compose.logger.UserCommand

class DefaultControllMapping : ControlMapping {
    private val controlKeysMapping = mapOf(
            "h" to UserCommand.Control.SwitchHelp,
            "j" to UserCommand.Control.SwitchTimestamp,
            "i" to UserCommand.Control.SwitchSearchFor)

    override fun isControlKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.character?.toString() in controlKeysMapping.keys
    }

    override fun mapControlKey(keyStroke: KeyStroke): UserCommand.Control {
        return controlKeysMapping[keyStroke.character.toString()]!!
    }

    override fun getControlMapping(): List<String> {
        return listOf(
                "h - show/hide this help",
                "j - show/hide timestamps",
                "i - turn on filter")
    }
}