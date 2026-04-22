package com.example.akillidersasistani

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class MathSoundManager(context: Context) {
    private val soundPool: SoundPool
    private val sounds = HashMap<String, Int>()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attrs)
            .build()


        sounds["correct"] = soundPool.load(context, R.raw.snd_correct, 1)
        sounds["wrong"] = soundPool.load(context, R.raw.snd_wrong, 1)
        sounds["game_over"] = soundPool.load(context, R.raw.snd_gameover, 1)
    }

    fun play(name: String) {
        sounds[name]?.let { soundPool.play(it, 1f, 1f, 0, 0, 1f) }
    }

    fun release() {
        soundPool.release()
    }
}