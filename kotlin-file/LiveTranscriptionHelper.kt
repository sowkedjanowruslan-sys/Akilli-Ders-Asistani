package com.example.akillidersasistani
import android.content.Context
import androidx.core.content.getSystemService

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class LiveTranscriptionHelper(
    private val context: Context,
    private val sessionCode: String,
    private val onResult: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference

    fun start() {
        // HATA DÜZELTME: stop() parametre almaz, parantez içini boşalttık.
        // Ayrıca işlemleri ana thread (Main Thread) üzerinde yapmalıyız.
        Handler(Looper.getMainLooper()).post {
            try {
                if (speechRecognizer != null) stop()

                // SES HASSASİYETİNİ ARTIRMA (Masa/Cep Modu için)
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true // Mikrofona "Uzak sesleri topla" emri verir

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

                    // Hoca masadan uzaklaşırsa hemen kesmesin (5 saniye bekleme)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)

                    // Arka plan gürültüsünü (sınıf uğultusu) filtrele:
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            // Firebase'e anlık metni bas
                            db.child("sessions").child(sessionCode).child("live_text").setValue(matches[0])
                        }
                    }

                    // LiveTranscriptionHelper.kt içinde onResults ve onError kısımlarını şu şekilde güncelle:

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val finalSentence = matches[0]
                            // Firebase'e son cümleyi bas
                            db.child("sessions").child(sessionCode).child("live_text").setValue(finalSentence)
                            onResult(finalSentence)
                        }

                        // KRİTİK: Dinleme bittiği için 500ms sonra tekrar başlat
                        Handler(Looper.getMainLooper()).postDelayed({
                            start()
                        }, 500)
                    }

                    override fun onError(error: Int) {
                        Log.e("STT_ERROR", "Hata Kodu: $error")

                        // Hata kodları: 6 (Timeout), 7 (No match), 8 (Busy)
                        // Bu hatalar olduğunda sistem durur. Yeniden başlatmamız lazım.
                        when (error) {
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    start()
                                }, 500)
                            }
                            else -> {
                                // Diğer ağır hatalarda 2 saniye bekle ve canlandır
                                Handler(Looper.getMainLooper()).postDelayed({
                                    start()
                                }, 2000)
                            }
                        }
                    }

                    // Gereksiz metodlar
                    override fun onReadyForSpeech(p0: Bundle?) { Log.d("STT", "Dinlemeye Hazır...") }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(p0: Float) {}
                    override fun onBufferReceived(p0: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(p0: Int, p1: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)

            } catch (e: Exception) {
                Log.e("STT_CRITICAL", "Başlatma hatası: ${e.message}")
            }
        }
    }

    fun stop() {
        Handler(Looper.getMainLooper()).post {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }
}