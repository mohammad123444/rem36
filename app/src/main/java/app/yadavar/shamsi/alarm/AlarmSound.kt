package app.yadavar.shamsi.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

object AlarmSound {
    /**
     * Prepared, looping player for the chosen sound (a phone ringtone or a song picked from storage).
     * Falls back to the phone's default alarm sound if the chosen one cannot be played.
     */
    fun create(context: Context, uriString: String): MediaPlayer? {
        val candidates = buildList<Uri> {
            if (uriString.isNotBlank()) add(Uri.parse(uriString))
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let { add(it) }
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let { add(it) }
        }
        for (uri in candidates) {
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                player.setDataSource(context, uri)
                player.isLooping = true
                player.setVolume(0.2f, 0.2f)
                player.prepare()
                return player
            } catch (e: Exception) {
                runCatching { player.release() }
            }
        }
        return null
    }
}
