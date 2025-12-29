package com.toqsoft.freechat.coreNetwork

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceFeedback(context: Context) {

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)
            }
        }
    }

    fun speak(text: String) {
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "VOICE_FEEDBACK"
        )
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
    }
}
