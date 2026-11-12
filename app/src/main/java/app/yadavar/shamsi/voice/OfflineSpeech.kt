package app.yadavar.shamsi.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** The microphone could not be opened (busy or no permission). */
class MicrophoneUnavailable : Exception("microphone unavailable")

/**
 * Offline Persian speech to text: the Shenava model (NVIDIA FastConformer, CTC) running on the phone with sherpa-onnx.
 *
 * The model files (model.onnx + tokens.txt, about 117 MB) are downloaded once, the first time the microphone
 * is used, into the app's private storage. After that no internet is needed at all.
 */
object OfflineSpeech {
    /** Shenava Rizeh v1.0 (32M parameters), Hugging Face repository in sherpa-onnx format. Apache-2.0 license. */
    private const val REPO = "https://huggingface.co/Reza2kn/Shenava-Rizeh-v1.0-sherpa-onnx/resolve/main/"
    private const val MODEL_URL = REPO + "model.onnx"
    private const val TOKENS_URL = REPO + "tokens.txt"
    private const val SAMPLE_RATE = 16_000
    private const val MAX_SECONDS = 30
    private const val SPEECH_LEVEL = 0.012f
    private const val END_SILENCE_MS = 1_600
    private const val NO_SPEECH_MS = 9_000L

    private fun dir(context: Context) = File(context.filesDir, "shenava")
    private fun modelFile(context: Context) = File(dir(context), "model.onnx")
    private fun tokensFile(context: Context) = File(dir(context), "tokens.txt")

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    fun isReady(context: Context): Boolean =
        modelFile(context).length() > 5_000_000L && tokensFile(context).length() > 100L

    /** Downloads the model once. [onProgress] gets 0..1, or -1 when the size is unknown. Returns false on failure. */
    suspend fun download(context: Context, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            dir(context).mkdirs()
            if (tokensFile(context).length() <= 100L) fetch(TOKENS_URL, tokensFile(context)) { }
            if (modelFile(context).length() <= 5_000_000L) fetch(MODEL_URL, modelFile(context), onProgress)
            isReady(context)
        }.getOrDefault(false)
    }

    private suspend fun fetch(address: String, target: File, onProgress: (Float) -> Unit) {
        val part = File(target.parentFile, target.name + ".part")
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        if (!kotlin.coroutines.coroutineContext.isActive) {
                            part.delete()
                            error("cancelled")
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastReport > 150) {
                            lastReport = now
                            onProgress(if (total > 0) done.toFloat() / total else -1f)
                        }
                    }
                }
            }
            if (!part.renameTo(target)) {
                target.delete()
                part.renameTo(target)
            }
            onProgress(1f)
        } finally {
            connection.disconnect()
        }
    }

    @Synchronized
    private fun load(context: Context): OfflineRecognizer {
        recognizer?.let { return it }
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = modelFile(context).absolutePath),
                tokens = tokensFile(context).absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
        )
        val created = OfflineRecognizer(config = config)
        recognizer = created
        return created
    }

    /** Deletes the downloaded model (used when the files are damaged). */
    fun reset(context: Context) {
        recognizer?.let { runCatching { it.release() } }
        recognizer = null
        runCatching { dir(context).deleteRecursively() }
    }

    /**
     * Records from the microphone until the person stops speaking (or [stop] returns true), then recognizes it.
     * [onLevel] gets the loudness 0..1 for the animation, [onProcessing] is called when recording has ended.
     */
    suspend fun listen(
        context: Context,
        stop: () -> Boolean,
        onLevel: (Float) -> Unit,
        onProcessing: () -> Unit,
    ): String = withContext(Dispatchers.Default) {
        val engine = load(context)
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, SAMPLE_RATE),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw MicrophoneUnavailable()
        }
        val samples = FloatArray(SAMPLE_RATE * MAX_SECONDS)
        var total = 0
        try {
            val chunk = ShortArray(SAMPLE_RATE / 10)
            var spoke = false
            var silentMs = 0
            val startedAt = SystemClock.elapsedRealtime()
            record.startRecording()
            while (isActive && !stop() && total + chunk.size <= samples.size) {
                val read = record.read(chunk, 0, chunk.size)
                if (read <= 0) break
                var sum = 0.0
                for (index in 0 until read) {
                    val value = chunk[index] / 32768f
                    samples[total + index] = value
                    sum += (value * value).toDouble()
                }
                total += read
                val rms = sqrt(sum / read).toFloat()
                onLevel(min(1f, rms * 9f))
                if (rms > SPEECH_LEVEL) {
                    spoke = true
                    silentMs = 0
                } else if (spoke) {
                    silentMs += read * 1000 / SAMPLE_RATE
                    if (silentMs >= END_SILENCE_MS) break
                }
                if (!spoke && SystemClock.elapsedRealtime() - startedAt > NO_SPEECH_MS) break
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
        if (total < SAMPLE_RATE / 4) return@withContext ""
        onProcessing()
        val stream = engine.createStream()
        try {
            stream.acceptWaveform(samples.copyOf(total), SAMPLE_RATE)
            engine.decode(stream)
            engine.getResult(stream).text.trim()
        } finally {
            runCatching { stream.release() }
        }
    }
}
