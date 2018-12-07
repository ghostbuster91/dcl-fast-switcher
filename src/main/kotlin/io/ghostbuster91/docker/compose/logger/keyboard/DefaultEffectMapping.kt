package io.ghostbuster91.docker.compose.logger.keyboard

import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import io.ghostbuster91.docker.compose.logger.Effect

class DefaultEffectMapping : EffectsMapping {
    override fun isEffectKey(keyStroke: KeyStroke): Boolean {
        return keyStroke.keyType == KeyType.Enter
    }

    override fun mapEffectKey(keyStroke: KeyStroke): Effect {
        return Effect.Println
    }
}