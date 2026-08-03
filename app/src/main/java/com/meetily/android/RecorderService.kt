package com.meetily.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.sqrt

class RecorderService : Service() {

    private val binder = LocalBinder()
    private var audioRecord: AudioRecord? = null
    private var outputFile: File? = null
    private var outputStream: FileOutputStream? = null
    private var sampleRate: Int = 16000

    private val isRecordingAtomic = AtomicBoolean(false)
    private val isPausedAtomic = AtomicBoolean(false)
    private var startTimeMillis: Long = 0L
    private var pausedDurationMillis: Long = 0L
    private var pauseStartTimeMillis: Long = 0L
    private var totalBytesWritten: Long = 0L
    private var currentAmplitude: Float = 0f

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile.asStateFlow()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): RecorderService = this@RecorderService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startRecording(meetingId: Long, filePath: String, sampleRate: Int = 16000) {
        this.sampleRate = sampleRate
        this.totalBytesWritten = 0L
        this.pausedDurationMillis = 0L

        outputFile = File(filePath)
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            _recordingState.value = RecordingState.ERROR
            throw IllegalStateException("AudioRecord başlatılamadı")
        }

        outputStream = FileOutputStream(outputFile)
        outputStream?.write(ByteArray(44))

        audioRecord?.startRecording()
        runCatching { val sid = audioRecord!!.audioSessionId; if (android.media.audiofx.NoiseSuppressor.isAvailable()) android.media.audiofx.NoiseSuppressor.create(sid)?.setEnabled(true); if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) android.media.audiofx.AcousticEchoCanceler.create(sid)?.setEnabled(true); if (android.media.audiofx.AutomaticGainControl.isAvailable()) android.media.audiofx.AutomaticGainControl.create(sid)?.setEnabled(true) }
        isRecordingAtomic.set(true)
        isPausedAtomic.set(false)
        startTimeMillis = SystemClock.elapsedRealtime()

        startForeground(NOTIFICATION_ID, buildNotification())

        _recordingState.value = RecordingState.RECORDING
        _currentFile.value = outputFile

        recordingJob = serviceScope.launch {
            val buffer = ByteArray(bufferSize)
            var lastAmplitudeEmit = 0L

            while (isActive && isRecordingAtomic.get()) {
                if (isPausedAtomic.get()) {
                    kotlinx.coroutines.delay(100)
                    continue
                }

                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1

                if (bytesRead > 0) {
                    outputStream?.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAmplitudeEmit >= 50L) {
                        currentAmplitude = calculateRmsAmplitude(buffer, bytesRead)
                        _amplitude.emit(currentAmplitude)
                        lastAmplitudeEmit = now
                    }
                } else if (bytesRead < 0) {
                    break
                }
            }
        }

        startTimer()
    }

    fun stopRecording(): File? {
        isRecordingAtomic.set(false)
        recordingJob?.cancel()

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        outputStream?.flush()
        outputStream?.close()
        outputStream = null

        val file = outputFile

        if (file != null && file.exists() && totalBytesWritten > 0) {
            writeWavHeader(file, totalBytesWritten)
        }

        _recordingState.value = RecordingState.STOPPED
        _elapsedMillis.value = 0L

        stopForeground(STOP_FOREGROUND_REMOVE)

        return file
    }

    fun pauseRecording() {
        if (isRecordingAtomic.get() && !isPausedAtomic.get()) {
            isPausedAtomic.set(true)
            pauseStartTimeMillis = SystemClock.elapsedRealtime()
            _recordingState.value = RecordingState.PAUSED
        }
    }

    fun resumeRecording() {
        if (isRecordingAtomic.get() && isPausedAtomic.get()) {
            pausedDurationMillis += SystemClock.elapsedRealtime() - pauseStartTimeMillis
            isPausedAtomic.set(false)
            _recordingState.value = RecordingState.RECORDING
        }
    }

    private fun startTimer() {
        serviceScope.launch {
            while (isActive && isRecordingAtomic.get()) {
                if (!isPausedAtomic.get()) {
                    val totalPaused = if (isPausedAtomic.get()) {
                        pausedDurationMillis + (SystemClock.elapsedRealtime() - pauseStartTimeMillis)
                    } else {
                        pausedDurationMillis
                    }
                    _elapsedMillis.value = (SystemClock.elapsedRealtime() - startTimeMillis - totalPaused).coerceAtLeast(0L)
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun calculateRmsAmplitude(buffer: ByteArray, bytesRead: Int): Float {
        var sum = 0.0
        val sampleCount = bytesRead / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until bytesRead step 2) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            sum += sample.toDouble() * sample.toDouble()
        }

        val rms = sqrt(sum / sampleCount)
        return (rms / 32767.0).toFloat().coerceIn(0f, 1f)
    }

    private fun writeWavHeader(file: File, pcmDataLength: Long) {
        val raf = RandomAccessFile(file, "rw")
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8

        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.writeIntLE(36 + pcmDataLength.toInt())
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeIntLE(16)
        raf.writeShortLE(1)
        raf.writeShortLE(channels)
        raf.writeIntLE(sampleRate)
        raf.writeIntLE(byteRate)
        raf.writeShortLE(channels * bitsPerSample / 8)
        raf.writeShortLE(bitsPerSample)
        raf.writeBytes("data")
        raf.writeIntLE(pcmDataLength.toInt())
        raf.close()
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notif_channel),
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, getString(R.string.notif_channel))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_recording))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.notif_stop), stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        if (isRecordingAtomic.get()) {
            stopRecording()
        }
        recordingJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.meetily.android.ACTION_STOP"
        const val NOTIFICATION_ID = 1001
    }
}

enum class RecordingState {
    IDLE, RECORDING, PAUSED, STOPPED, ERROR
}
