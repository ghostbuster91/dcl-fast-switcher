package io.ghostbuster91.docker.compose.logger.keyboard.linux

import com.googlecode.lanterna.input.KeyStroke
import io.ghostbuster91.docker.compose.logger.Action
import io.ghostbuster91.docker.compose.logger.keyboard.*

class LinuxKeyboardLayout(serviceMapping: ServiceMappingImpl)
    : KeyboardLayout,
        RemoveServiceMapping by DefaultShiftRemoveServiceMapping(serviceMapping),
        AddServiceMapping by LinuxAltAddServiceMapping(serviceMapping),
        SingleServiceMapping by DefaultSingleServiceMapping(serviceMapping),
        ControlMapping by DefaultControllMapping(),
        ServiceMapping by serviceMapping,
        EffectsMapping by DefaultEffectMapping() {

    override fun mapCommand(keyStroke: KeyStroke): Action {
        return when {
            isRemoveKey(keyStroke) -> mapRemoveKey(keyStroke)
            isAddKey(keyStroke) -> mapAddKey(keyStroke)
            isSingleServiceKey(keyStroke) -> mapSingleServiceKey(keyStroke)
            isControlKey(keyStroke) -> mapControlKey(keyStroke)
            else -> Action.Unknown
        }
    }
}