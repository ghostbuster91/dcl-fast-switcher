package io.ghostbuster91.docker.compose.logger.keyboard

import com.googlecode.lanterna.input.KeyStroke
import io.ghostbuster91.docker.compose.logger.UserCommand

class DefaultShiftRemoveServiceMapping(private val serviceMapping: ServiceMapping) : RemoveServiceMapping {

    override fun isRemoveKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.character?.toString()
                ?.let {
                    serviceMapping.isServiceKey(it.toLowerCase())
                            && !keyStroke.isAltDown
                            && it.isUpperCase()
                }
                ?: false
    }

    override fun mapRemoveKey(keyStroke: KeyStroke): UserCommand.Remove {
        val service = serviceMapping.mapToService(keyStroke.character!!.toString().toLowerCase())
        return UserCommand.Remove(service)
    }

    fun String.isUpperCase(): Boolean {
        return this.toUpperCase() == this
    }
}