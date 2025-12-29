package com.toqsoft.freechat.featureVoiceListening

import android.R.attr.action
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceRecognitionHelper(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit
) {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SPEECH", "Listening started")
            }

            override fun onBeginningOfSpeech() {
                Log.d("SPEECH", "Speech detected")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d("SPEECH", "End of speech")
            }

            override fun onError(error: Int) {
                Log.e("SPEECH", "Error code: $error")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                Log.d("SPEECH", "Final result: $text")
                onFinalResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                Log.d("SPEECH", "Partial: $text")
                onPartialResult(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }

        speechRecognizer.startListening(intent)
    }


    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun destroy() {
        speechRecognizer.destroy()
    }
}
