package com.pico.junglechess.content

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.pico.junglechess.game.Animal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioSettings(
    val musicEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = true,
)

/** One streamed player per Stage; never belongs to the separate controls window. */
class BackgroundMusic(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("audio_settings", Application.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(AudioSettings(
        musicEnabled = preferences.getBoolean("music_enabled", true),
        soundEffectsEnabled = preferences.getBoolean("sound_effects_enabled", true),
    ))
    val settings = mutableSettings.asStateFlow()
    private val audio = application.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var active = false
    private var requestedFocus = false
    private var allowed = false
    private var ducked = false
    private var calls: SoundPool? = null
    private val sounds = mutableMapOf<Animal, Int>()
    private val loaded = mutableSetOf<Int>()
    private var stream = 0
    private var lastAnimal: Animal? = null
    private var lastCall = 0L
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (active && requestedFocus) {
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> { allowed = true; ducked = false }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { ducked = true }
                    else -> { allowed = false; ducked = false }
                }
                applyPlayback()
                if (!allowed) stopCall()
                else if (stream != 0) calls?.setVolume(stream,
                    if (ducked) .2f else .8f, if (ducked) .2f else .8f)
            }
        }, Handler(Looper.getMainLooper()))
        .build()

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (!value) {
            allowed = false
            stopCall()
            applyPlayback()
            if (requestedFocus) audio.abandonAudioFocusRequest(focus)
            requestedFocus = false
            ducked = false
            Log.i("JungleMusic", "PAUSED Stage hidden")
            return
        }
        updateFocus()
        if (player == null) prepare()
        if (calls == null) prepareCalls()
        applyPlayback()
    }

    fun setMusicEnabled(enabled: Boolean) {
        if (settings.value.musicEnabled == enabled) return
        mutableSettings.value = settings.value.copy(musicEnabled = enabled)
        preferences.edit().putBoolean("music_enabled", enabled).apply()
        updateFocus()
        applyPlayback()
        Log.i("JungleAudio", "musicEnabled=$enabled")
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        if (settings.value.soundEffectsEnabled == enabled) return
        mutableSettings.value = settings.value.copy(soundEffectsEnabled = enabled)
        preferences.edit().putBoolean("sound_effects_enabled", enabled).apply()
        if (!enabled) stopCall()
        lastAnimal = null
        lastCall = 0
        updateFocus()
        applyPlayback()
        Log.i("JungleAudio", "soundEffectsEnabled=$enabled")
    }

    private fun updateFocus() {
        val wanted = active && (settings.value.musicEnabled || settings.value.soundEffectsEnabled)
        if (!wanted) {
            allowed = false
            stopCall()
            if (requestedFocus) audio.abandonAudioFocusRequest(focus)
            requestedFocus = false
            ducked = false
        } else if (!requestedFocus) {
            allowed = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            requestedFocus = allowed
        }
    }

    private fun prepareCalls() {
        val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        calls = pool
        pool.setOnLoadCompleteListener { source, id, status ->
            if (calls === source && status == 0) loaded += id
        }
        for (animal in Animal.entries) {
            try {
                getApplication<Application>().assets.openFd("calls/${animal.name.lowercase()}.wav").use {
                    sounds[animal] = pool.load(it, 1)
                }
            } catch (error: Exception) {
                Log.e("JungleCalls", "Could not load $animal", error)
            }
        }
    }

    fun playAnimal(animal: Animal) {
        if (!active || !allowed || !settings.value.soundEffectsEnabled) return
        val pool = calls ?: return
        val id = sounds[animal]?.takeIf { it in loaded } ?: return
        val now = SystemClock.elapsedRealtime()
        if (lastAnimal == animal && now - lastCall < 300) return
        stopCall()
        val volume = if (ducked) .2f else .8f
        stream = pool.play(id, volume, volume, 1, 0, 1f)
        if (stream != 0) {
            lastAnimal = animal
            lastCall = now
            Log.i("JungleCalls", "PLAY animal=$animal stream=$stream")
        }
    }

    private fun stopCall() {
        if (stream != 0) calls?.stop(stream)
        stream = 0
    }

    private fun prepare() {
        try {
            val media = MediaPlayer()
            player = media
            media.setAudioAttributes(attributes)
            getApplication<Application>().assets.openFd("woodland_stroll.wav").use {
                media.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            media.isLooping = true
            media.setOnPreparedListener {
                if (player === it) {
                    prepared = true
                    Log.i("JungleMusic", "PREPARED duration=${it.duration}ms loop=${it.isLooping}")
                    applyPlayback()
                }
            }
            media.setOnErrorListener { _, what, extra ->
                Log.e("JungleMusic", "Audio unavailable what=$what extra=$extra")
                close()
                true
            }
            media.prepareAsync()
        } catch (error: Exception) {
            Log.e("JungleMusic", "Could not prepare background music", error)
            close()
        }
    }

    private fun applyPlayback() {
        val media = player ?: return
        if (!prepared) return
        val volume = if (ducked) .075f else .35f
        media.setVolume(volume, volume)
        if (active && allowed && settings.value.musicEnabled) {
            if (!media.isPlaying) {
                media.start()
                Log.i("JungleMusic", "PLAYING position=${media.currentPosition}")
            }
        } else if (media.isPlaying) {
            media.pause()
        }
    }

    fun close() {
        active = false
        allowed = false
        prepared = false
        stopCall()
        calls?.release()
        calls = null
        sounds.clear()
        loaded.clear()
        lastAnimal = null
        lastCall = 0
        player?.release()
        player = null
        if (requestedFocus) audio.abandonAudioFocusRequest(focus)
        requestedFocus = false
        ducked = false
        Log.i("JungleMusic", "RELEASED")
    }

    override fun onCleared() { close() }
}
