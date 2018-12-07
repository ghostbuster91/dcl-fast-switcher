package io.ghostbuster91.docker.compose.logger.keyboard.linux

import io.ghostbuster91.docker.compose.logger.keyboard.*

class LinuxKeyboardLayout(serviceMapping: ServiceMappingImpl)
    : KeyboardLayout,
        RemoveServiceMapping by DefaultShiftRemoveServiceMapping(serviceMapping),
        AddServiceMapping by LinuxAltAddServiceMapping(serviceMapping),
        SingleServiceMapping by DefaultSingleServiceMapping(serviceMapping),
        ControlMapping by DefaultControllMapping(),
        ServiceMapping by serviceMapping,
        EffectsMapping by DefaultEffectMapping()