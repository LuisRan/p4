package mx.ipn.escom.circuito.core

import mx.ipn.escom.circuito.platform.Haptic
import mx.ipn.escom.circuito.platform.PlatformServices

/** Sonido + vibración respetando los ajustes del usuario. */
class GameFeedback(private val settings: AppSettings, private val platform: PlatformServices) {
    fun play(s: Sfx) {
        if (settings.sound) platform.playWav(s.name, SoundBank.wav(s))
    }

    fun haptic(strong: Boolean = false) {
        if (settings.vibration) platform.haptic(if (strong) Haptic.STRONG else Haptic.SELECT)
    }
}
