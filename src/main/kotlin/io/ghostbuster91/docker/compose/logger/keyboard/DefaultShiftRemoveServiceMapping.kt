package io.ghostbuster91.docker.compose.logger.keyboard

import com.googlecode.lanterna.input.KeyStroke
import io.ghostbuster91.docker.compose.logger.UserCommand

class DefaultShiftRemoveServiceMapping(private val serviceMapping: ServiceMapping) : RemoveServiceMapping {

    override fun isRemoveKey(keyStroke: KeyStroke): Boolean {
        return serviceMapping.isServiceKey(keyStroke.character?.toString()?.toLowerCase())
            && !keyStroke.isAltDown
    }

    override fun mapRemoveKey(keyStroke: KeyStroke): UserCommand.Remove {
        val service = serviceMapping.mapToService(keyStroke.character!!.toString().toLowerCase())
        return UserCommand.Remove(service)
    }
}