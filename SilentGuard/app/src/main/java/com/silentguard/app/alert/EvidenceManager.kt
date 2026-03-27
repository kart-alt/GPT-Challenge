package com.silentguard.app.alert

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class EvidenceManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    companion object {
        private const val TAG = "EvidenceManager"
    }

    /**
     * Starts recording audio securely to the app's internal cache
     */
    fun startRecording() {
        if (isRecording) return

        try {
            // Setup output file
            val cacheDir = File(context.cacheDir, "evidence")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputFile = File(cacheDir, "Evidence_${timestamp}.3gp")

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.i(TAG, "Started evidence recording: ${outputFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start evidence recording", e)
            stopRecording()
        }
    }

    /**
     * Stops recording and releases resources
     */
    fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.i(TAG, "Stopped evidence recording")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording cleanly", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
    }

    fun release() {
        stopRecording()
    }
}
