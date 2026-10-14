package com.example.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
class VideoPlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    var exoPlayer: ExoPlayer? = null
        private set

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var lastLoadedUri: String? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return
        val player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                        }
                        Player.STATE_READY -> {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                durationMs = duration.coerceAtLeast(0L),
                                errorMessage = null
                            )
                        }
                        Player.STATE_ENDED -> {
                            _state.value = _state.value.copy(isPlaying = false, isLoading = false)
                        }
                        Player.STATE_IDLE -> {
                            _state.value = _state.value.copy(isLoading = false)
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(isPlaying = isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isPlaying = false,
                        errorMessage = "Playback error: ${error.localizedMessage ?: "Codec or file issue"}"
                    )
                }
            })
        }
        exoPlayer = player
        startPositionPolling()
    }

    fun loadMedia(uriString: String, playWhenReady: Boolean = false) {
        lastLoadedUri = uriString
        val player = exoPlayer ?: return
        try {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val uri = Uri.parse(uriString)
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = playWhenReady
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = "Failed to load video: ${e.message}"
            )
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val clamped = positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(clamped)
        _state.value = _state.value.copy(currentPositionMs = clamped)
    }

    fun seekBy(deltaMs: Long) {
        val player = exoPlayer ?: return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        _state.value = _state.value.copy(currentPositionMs = target)
    }

    fun toggleMute() {
        val player = exoPlayer ?: return
        val currentMuted = _state.value.isMuted
        val newMuted = !currentMuted
        player.volume = if (newMuted) 0f else _state.value.volume
        _state.value = _state.value.copy(isMuted = newMuted)
    }

    fun setSpeed(speed: Float) {
        val player = exoPlayer ?: return
        player.playbackParameters = PlaybackParameters(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun retry() {
        lastLoadedUri?.let { loadMedia(it, playWhenReady = true) }
    }

    private fun startPositionPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.playbackState == Player.STATE_READY) {
                        _state.value = _state.value.copy(
                            currentPositionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(50) // Smooth 20fps timeline tracking
            }
        }
    }

    fun release() {
        pollingJob?.cancel()
        pollingJob = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
