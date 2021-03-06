package io.ghostbuster91.docker.compose.logger.keyboard

import com.googlecode.lanterna.input.KeyStroke
import io.ghostbuster91.docker.compose.logger.Action
import io.ghostbuster91.docker.compose.logger.Effect
import io.ghostbuster91.docker.compose.logger.UserCommand

interface KeyboardLayout : AddServiceMapping, RemoveServiceMapping, ControlMapping, SingleServiceMapping, ServiceMapping, EffectsMapping {
    fun mapCommand(keyStroke: KeyStroke) : Action
}

interface AddServiceMapping {
    fun isAddKey(keyStroke: KeyStroke): Boolean
    fun mapAddKey(keyStroke: KeyStroke): UserCommand.Add
}

interface RemoveServiceMapping {
    fun isRemoveKey(keyStroke: KeyStroke): Boolean
    fun mapRemoveKey(keyStroke: KeyStroke): UserCommand.Remove
}

interface ControlMapping {
    fun isControlKey(keyStroke: KeyStroke): Boolean
    fun mapControlKey(keyStroke: KeyStroke): UserCommand.Control
    fun getControlMapping() : List<String>
}

interface SingleServiceMapping {
    fun isSingleServiceKey(keyStroke: KeyStroke): Boolean
    fun mapSingleServiceKey(keyStroke: KeyStroke): UserCommand.Single
}

interface EffectsMapping {
    fun isEffectKey(keyStroke: KeyStroke) : Boolean
    fun mapEffectKey(keyStroke: KeyStroke) : Effect
}