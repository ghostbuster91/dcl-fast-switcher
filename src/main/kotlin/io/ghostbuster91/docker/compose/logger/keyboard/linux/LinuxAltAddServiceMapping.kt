package io.ghostbuster91.docker.compose.logger.keyboard.linux

import com.googlecode.lanterna.input.KeyStroke
import io.ghostbuster91.docker.compose.logger.UserCommand
import io.ghostbuster91.docker.compose.logger.keyboard.AddServiceMapping
import io.ghostbuster91.docker.compose.logger.keyboard.ServiceMapping

class LinuxAltAddServiceMapping(private val serviceMapping: ServiceMapping) : AddServiceMapping {
    override fun isAddKey(keyStroke: KeyStroke): Boolean {
        return serviceMapping.isServiceKey(keyStroke.character?.toString())
                && keyStroke.isAltDown
    }

    override fun mapAddKey(keyStroke: KeyStroke): UserCommand.Add {
        val service = serviceMapping.mapToService(keyStroke.character!!.toString())
        return UserCommand.Add(service)
    }
}