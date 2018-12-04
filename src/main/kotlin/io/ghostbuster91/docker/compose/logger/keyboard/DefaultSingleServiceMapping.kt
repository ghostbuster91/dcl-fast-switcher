package io.ghostbuster91.docker.compose.logger.keyboard

import com.googlecode.lanterna.input.KeyStroke
import io.ghostbuster91.docker.compose.logger.UserCommand

class DefaultSingleServiceMapping(private val serviceMapping: ServiceMapping) : SingleServiceMapping {

    override fun isSingleServiceKey(keyStroke: KeyStroke): Boolean {
        return serviceMapping.isServiceKey(keyStroke.character?.toString())
                && !keyStroke.isAltDown
                && !keyStroke.isShiftDown
    }

    override fun mapSingleServiceKey(keyStroke: KeyStroke): UserCommand.Single {
        val service = serviceMapping.mapToService(keyStroke.character!!.toString())
        return UserCommand.Single(service)
    }
}