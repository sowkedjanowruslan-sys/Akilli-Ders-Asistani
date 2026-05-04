package com.example.akillidersasistani

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class AudioRecorderHandler(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var transcriptBuffer = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Callbacks
    var onTranscriptUpdate: ((String) -> Unit)? = null
    var onVolumeUpdate: ((Float) -> Unit)? = null

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            setupSpeechListener()
        }
    }

    private fun setupSpeechListener() {
        val intent = createSpeechIntent()

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    transcriptBuffer.append(" ").append(text)
                    onTranscriptUpdate?.invoke(text)
                }
                // Dinlemeyi yeniden başlat (Sürekli dinleme için)
                speechRecognizer?.startListening(intent)
            }

            override fun onPartialResults(bundle: Bundle?) {
                val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    onTranscriptUpdate?.invoke(matches[0])
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Main thread'e gönderiyoruz
                mainHandler.post {
                    onVolumeUpdate?.invoke(rmsdB)
                }
            }

            override fun onError(error: Int) {
                // Hata durumunda (örneğin sessizlik) tekrar başlat
                Log.e("SpeechRecognizer", "Hata kodu: $error")
                speechRecognizer?.startListening(intent)
            }

            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
    }

    private fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun startRecording() {
        // NOT: Eğer yüksek kaliteli ses kaydı (.mp3) istiyorsanız
        // SpeechRecognizer'ı KULLANAMAZSINIZ. Android mikrofonu tek bir yere bağlar.
        // Bu yüzden burada sadece SpeechRecognizer'ı başlatıyoruz:

        transcriptBuffer.setLength(0) // Eski kaydı temizle
        val intent = createSpeechIntent()
        speechRecognizer?.startListening(intent)
    }

    fun stopRecording(): String {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        return transcriptBuffer.toString().trim()
    }
}