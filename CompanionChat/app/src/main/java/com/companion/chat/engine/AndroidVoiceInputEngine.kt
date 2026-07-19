package com.companion.chat.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.companion.chat.data.engine.VoiceInputEngine
import com.companion.chat.data.engine.VoiceInputEvent
import com.companion.chat.data.voice.CloudAsrConfigRepository
import com.companion.chat.data.voice.LocalSenseVoiceModelStatus
import com.companion.chat.data.voice.VoiceInputBackend
import com.companion.chat.data.voice.VoiceInputConfig
import com.companion.chat.data.voice.VoiceInputConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class AndroidVoiceInputEngine(
    private val context: Context,
    private val configRepository: VoiceInputConfigRepository = VoiceInputConfigRepository(context),
    private val cloudAsrEngine: CloudHttpAsrEngine = CloudHttpAsrEngine(CloudAsrConfigRepository(context))
) : VoiceInputEngine {

    private val _events = MutableSharedFlow<VoiceInputEvent>(extraBufferCapacity = 16)
    override val events: Flow<VoiceInputEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: AudioRecord? = null
    @Volatile
    private var isListening = false
    @Volatile
    private var stopRequested = false
    @Volatile
    private var cachedMnnRecognizer: MnnSenseVoiceRecognizer? = null
    @Volatile
    private var cachedMnnModelDir: String? = null

    override fun warmUp() {
        val config = configRepository.getConfig()
        if (config.backend == VoiceInputBackend.CLOUD_HTTP_ASR) {
            _events.tryEmit(VoiceInputEvent.WarmedUp)
            return
        }

        emitLocalModelStatus(config)
    }

    private fun emitLocalModelStatus(config: VoiceInputConfig): Boolean {
        return when (val status = configRepository.getLocalSenseVoiceModelStatus(config)) {
            LocalSenseVoiceModelStatus.Ready -> {
                _events.tryEmit(VoiceInputEvent.WarmedUp)
                true
            }
            LocalSenseVoiceModelStatus.DirectoryNotConfigured -> {
                _events.tryEmit(VoiceInputEvent.Error("本地 SenseVoice 模型未配置"))
                false
            }
            is LocalSenseVoiceModelStatus.MissingFiles -> {
                _events.tryEmit(VoiceInputEvent.Error("本地 SenseVoice 模型文件缺失: ${status.fileNames.joinToString()}"))
                false
            }
        }
    }

    override fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _events.tryEmit(VoiceInputEvent.Error("缺少录音权限"))
            return
        }

        val config = configRepository.getConfig()
        Log.i(TAG, "startListening: backend=${config.backend}, modelDir=${config.localSenseVoiceModelDirectory}")
        fileLog("startListening: backend=${config.backend}, modelDir=${config.localSenseVoiceModelDirectory}")
        if (config.backend != VoiceInputBackend.CLOUD_HTTP_ASR && !emitLocalModelStatus(config)) {
            Log.e(TAG, "startListening: model status check failed, aborting")
            fileLog("startListening: model status check failed, aborting")
            return
        }

        isListening = true
        stopRequested = false
        _events.tryEmit(VoiceInputEvent.Listening)
        scope.launch {
            runCatching {
                Log.i(TAG, "开始录音...")
                fileLog("开始录音...")
                val audio = recordUntilSilence(config)
                if (stopRequested) {
                    Log.i(TAG, "录音被用户停止")
                    fileLog("录音被用户停止")
                    return@launch
                }
                if (audio.isEmpty) {
                    Log.e(TAG, "录音为空（未检测到语音）")
                    fileLog("录音为空（未检测到语音）")
                    _events.tryEmit(VoiceInputEvent.Error("未检测到语音"))
                    return@launch
                }
                Log.i(TAG, "录音完成: ${audio.pcm16.size} samples, sr=${audio.sampleRate}")
                fileLog("录音完成: ${audio.pcm16.size} samples, sr=${audio.sampleRate}")
                val text = withContext(Dispatchers.IO) {
                    when (config.backend) {
                        VoiceInputBackend.LOCAL_SENSEVOICE -> {
                            SherpaOnnxSenseVoiceRecognizer(
                                assetManager = null,
                                resolveSenseVoiceModelFiles(config.localSenseVoiceModelDirectory)
                            ).transcribe(audio)
                        }
                        VoiceInputBackend.LOCAL_MNN_SENSEVOICE -> {
                            Log.i(TAG, "开始 MNN ASR 识别...")
                            fileLog("开始 MNN ASR 识别...")
                            val modelDir = config.localSenseVoiceModelDirectory
                            val recognizer = cachedMnnRecognizer?.takeIf { modelDir == cachedMnnModelDir }
                                ?: MnnSenseVoiceRecognizer(
                                    resolveMnnAsrModelFiles(modelDir)
                                ).also {
                                    cachedMnnRecognizer = it
                                    cachedMnnModelDir = modelDir
                                }
                            val result = recognizer.transcribe(audio)
                            fileLog("MNN ASR 识别结果: '$result'")
                            result
                        }
                        VoiceInputBackend.CLOUD_HTTP_ASR -> cloudAsrEngine.transcribe(audio)
                    }
                }
                Log.i(TAG, "识别结果: '$text'")
                fileLog("最终识别结果: '$text'")
                if (text.isBlank()) {
                    _events.tryEmit(VoiceInputEvent.Error("未识别到文本"))
                } else {
                    _events.tryEmit(VoiceInputEvent.FinalResult(text))
                }
            }.getOrElse { throwable ->
                if (stopRequested) {
                    return@launch
                }
                Log.e(TAG, "语音输入失败", throwable)
                fileLog("语音输入失败: ${throwable.javaClass.simpleName}: ${throwable.message}")
                _events.tryEmit(VoiceInputEvent.Error(throwable.message ?: "语音输入失败"))
            }
            isListening = false
            releaseRecorder()
            _events.tryEmit(VoiceInputEvent.NotListening)
        }
    }

    override fun stopListening() {
        stopRequested = true
        isListening = false
        runCatching {
            recorder?.stop()
        }
        _events.tryEmit(VoiceInputEvent.NotListening)
    }

    override fun release() {
        stopListening()
        scope.cancel()
        cachedMnnRecognizer?.release()
        cachedMnnRecognizer = null
    }

    private fun recordUntilSilence(config: VoiceInputConfig): RecordedAudio {
        if (config.backend == VoiceInputBackend.CLOUD_HTTP_ASR) {
            return recordFixedWindow()
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, SAMPLE_RATE / 2)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        recorder = audioRecord

        val buffer = ShortArray(FRAME_SAMPLES)
        var totalFrames = 0
        // MNN 后端不依赖 sherpa-onnx，使用能量 VAD
        val useEnergyVad = config.backend == VoiceInputBackend.LOCAL_MNN_SENSEVOICE
        val vad: Any = if (useEnergyVad) {
            EnergyVad(sampleRate = SAMPLE_RATE, threshold = 0.02f, minSpeechDuration = 0.3f, maxSpeechDuration = 15.0f)
        } else {
            SherpaOnnxSileroVad(
                assetManager = null,
                configValues = SileroVadConfigValues(
                    model = resolveSenseVoiceModelFiles(config.localSenseVoiceModelDirectory).vad
                )
            )
        }

        try {
            audioRecord.startRecording()
            fileLog("AudioRecord started, useEnergyVad=$useEnergyVad, frameSamples=$FRAME_SAMPLES, maxFrames=$MAX_FRAMES")
            while (!stopRequested && isListening && totalFrames < MAX_FRAMES && scope.isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                totalFrames += 1
                val samples = AudioPcmConverter.pcm16ToFloatArray(buffer, read)
                if (useEnergyVad) {
                    (vad as EnergyVad).acceptWaveform(samples)
                    val segment = (vad as EnergyVad).drainSegments().firstOrNull { audio -> !audio.isEmpty }
                    if (segment != null) {
                        fileLog("EnergyVad detected speech: ${segment.pcm16.size} samples after $totalFrames frames")
                        return segment
                    }
                } else {
                    (vad as SherpaOnnxSileroVad).acceptWaveform(samples)
                    val segment = (vad as SherpaOnnxSileroVad).drainSegments().firstOrNull { audio -> !audio.isEmpty }
                    if (segment != null) {
                        fileLog("SileroVad detected speech: ${segment.pcm16.size} samples after $totalFrames frames")
                        return segment
                    }
                }
            }
            fileLog("Recording loop ended: totalFrames=$totalFrames, stopRequested=$stopRequested, isListening=$isListening")
            if (useEnergyVad) {
                (vad as EnergyVad).flush()
                val flushed = (vad as EnergyVad).drainSegments().firstOrNull { audio -> !audio.isEmpty }
                fileLog("EnergyVad flush: ${flushed?.pcm16?.size ?: 0} samples")
                return flushed ?: RecordedAudio(ShortArray(0), SAMPLE_RATE)
            } else {
                (vad as SherpaOnnxSileroVad).flush()
                return (vad as SherpaOnnxSileroVad).drainSegments().firstOrNull { audio -> !audio.isEmpty }
                    ?: RecordedAudio(ShortArray(0), SAMPLE_RATE)
            }
        } catch (e: IllegalStateException) {
            if (stopRequested) {
                return RecordedAudio(ShortArray(0), SAMPLE_RATE)
            }
            throw e
        } catch (e: Exception) {
            if (stopRequested) {
                return RecordedAudio(ShortArray(0), SAMPLE_RATE)
            }
            throw IllegalStateException(e.message ?: "语音录制失败", e)
        } finally {
            if (useEnergyVad) {
                (vad as EnergyVad).release()
            } else {
                (vad as SherpaOnnxSileroVad).release()
            }
        }
    }

    private fun recordFixedWindow(): RecordedAudio {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, SAMPLE_RATE / 2)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        recorder = audioRecord

        val buffer = ShortArray(FRAME_SAMPLES)
        val samples = mutableListOf<Short>()
        var totalFrames = 0

        try {
            audioRecord.startRecording()
            while (!stopRequested && isListening && totalFrames < CLOUD_MAX_FRAMES && scope.isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                totalFrames += 1
                repeat(read) { index ->
                    samples += buffer[index]
                }
            }
            return RecordedAudio(samples.toShortArray(), SAMPLE_RATE)
        } catch (e: IllegalStateException) {
            if (stopRequested) {
                return RecordedAudio(ShortArray(0), SAMPLE_RATE)
            }
            throw e
        } catch (e: Exception) {
            if (stopRequested) {
                return RecordedAudio(ShortArray(0), SAMPLE_RATE)
            }
            throw IllegalStateException(e.message ?: "语音录制失败", e)
        }
    }

    private fun releaseRecorder() {
        val audioRecord = recorder
        recorder = null
        runCatching {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
        }
        runCatching {
            audioRecord?.release()
        }
    }

    private fun fileLog(message: String) {
        runCatching {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            context.openFileOutput("voice_input_log.txt", android.content.Context.MODE_APPEND).use { output ->
                output.write("[$time] $message\n".toByteArray())
            }
        }
    }

    private companion object {
        const val TAG = "VoiceInputEngine"
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 512
        const val MAX_RECORDING_MILLIS = 15_000
        const val MAX_FRAMES = MAX_RECORDING_MILLIS / (FRAME_SAMPLES * 1000 / SAMPLE_RATE)
        const val CLOUD_RECORDING_MILLIS = 5_000
        const val CLOUD_MAX_FRAMES = CLOUD_RECORDING_MILLIS / (FRAME_SAMPLES * 1000 / SAMPLE_RATE)
    }
}
