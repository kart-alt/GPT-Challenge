package com.silentguard.app.detection

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Audio-based distress detection using TensorFlow Lite
 * 
 * Architecture:
 * 1. Capture 2-second audio windows
 * 2. Extract Mel spectrogram features
 * 3. Run through YAMNet-inspired model
 * 4. Output distress probability (0-1)
 */
class AudioClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    // Audio configuration
    private val sampleRate = 16000  // 16kHz (optimal for speech)
    private val windowSize = 2      // seconds
    private val bufferSize = sampleRate * windowSize
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    // Feature extraction parameters
    private val nMels = 64          // Mel filterbank size
    private val nFFT = 512          // FFT window size
    private val hopLength = 160     // 10ms hop (16000 / 100)
    
    // Distress detection thresholds
    private val distressThreshold = 0.6f
    private val historySize = 5
    private val scoreHistory = ArrayDeque<Float>(historySize)

    companion object {
        private const val TAG = "AudioClassifier"
        private const val MODEL_NAME = "distress_audio_model.tflite"
    }

    /**
     * Initialize the model (call once on startup)
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load TFLite model
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(true)  // Use Android Neural Networks API if available
            })
            
            Log.i(TAG, "Audio classifier initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio classifier", e)
            false
        }
    }

    /**
     * Start continuous audio monitoring
     */
    fun startMonitoring(onDistressDetected: (Float) -> Unit) {
        if (isRecording) return
        
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val recordBufferSize = maxOf(minBufferSize, bufferSize * 2)
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                recordBufferSize
            )
            
            audioRecord?.startRecording()
            isRecording = true
            
            // Process audio in background
            CoroutineScope(Dispatchers.IO).launch {
                processAudioStream(onDistressDetected)
            }
            
            Log.i(TAG, "Audio monitoring started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
        }
    }

    /**
     * Stop audio monitoring
     */
    fun stopMonitoring() {
        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        Log.i(TAG, "Audio monitoring stopped")
    }

    /**
     * Continuous audio stream processing
     */
    private suspend fun processAudioStream(onDistressDetected: (Float) -> Unit) {
        val audioBuffer = ShortArray(bufferSize)
        
        while (isRecording && audioRecord != null) {
            val readResult = audioRecord?.read(audioBuffer, 0, bufferSize) ?: -1
            
            if (readResult > 0) {
                // Convert to float and normalize
                val floatBuffer = audioBuffer.map { it / 32768f }.toFloatArray()
                
                // Analyze for distress
                val distressScore = analyzeAudio(floatBuffer)
                
                // Update history
                scoreHistory.add(distressScore)
                if (scoreHistory.size > historySize) {
                    scoreHistory.removeFirst()
                }
                
                // Moving average for stability
                val avgScore = scoreHistory.average().toFloat()
                
                if (avgScore > distressThreshold) {
                    onDistressDetected(avgScore)
                }
            }
            
            delay(100) // Small delay to prevent CPU overuse
        }
    }

    /**
     * Analyze audio buffer for distress signals
     * 
     * @param audioBuffer Raw audio samples (normalized -1 to 1)
     * @return Distress probability (0-1)
     */
    fun analyzeAudio(audioBuffer: FloatArray): Float {
        try {
            // Step 1: Pre-emphasis filter (boost high frequencies)
            val emphasized = preEmphasis(audioBuffer)
            
            // Step 2: Extract Mel spectrogram
            val melSpectrogram = extractMelSpectrogram(emphasized)
            
            // Step 3: Run through model
            val distressScore = runInference(melSpectrogram)
            
            // Step 4: Apply post-processing
            return postProcess(distressScore, audioBuffer)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing audio", e)
            return 0f
        }
    }

    /**
     * Pre-emphasis filter to amplify high frequencies
     * Helps detect screams (typically 1-4kHz range)
     */
    private fun preEmphasis(signal: FloatArray, coefficient: Float = 0.97f): FloatArray {
        val output = FloatArray(signal.size)
        output[0] = signal[0]
        
        for (i in 1 until signal.size) {
            output[i] = signal[i] - coefficient * signal[i - 1]
        }
        
        return output
    }

    /**
     * Extract Mel-frequency spectrogram
     * Converts audio to frequency representation similar to human hearing
     */
    private fun extractMelSpectrogram(audio: FloatArray): Array<FloatArray> {
        val numFrames = (audio.size - nFFT) / hopLength + 1
        val spectrogram = Array(nMels) { FloatArray(numFrames) }
        
        // Hann window for FFT
        val window = hannWindow(nFFT)
        
        for (frameIdx in 0 until numFrames) {
            val start = frameIdx * hopLength
            val frame = audio.sliceArray(start until min(start + nFFT, audio.size))
            
            // Apply window
            val windowedFrame = frame.mapIndexed { i, value -> 
                value * window[i] 
            }.toFloatArray()
            
            // Compute FFT magnitude
            val fftMag = fftMagnitude(windowedFrame)
            
            // Convert to Mel scale
            val melFrame = toMelScale(fftMag)
            
            // Fill spectrogram
            for (melBin in 0 until nMels) {
                spectrogram[melBin][frameIdx] = melFrame[melBin]
            }
        }
        
        return spectrogram
    }

    /**
     * Hann window for FFT
     */
    private fun hannWindow(size: Int): FloatArray {
        return FloatArray(size) { i ->
            (0.5 * (1 - cos(2 * PI * i / (size - 1)))).toFloat()
        }
    }

    /**
     * Simplified FFT magnitude (real-world: use FFTW or KissFFT library)
     * For competition demo, we'll use approximation
     */
    private fun fftMagnitude(signal: FloatArray): FloatArray {
        val magnitudes = FloatArray(nFFT / 2)
        
        // Simplified: Energy in frequency bins (NOT true FFT, but fast approximation)
        for (k in magnitudes.indices) {
            var real = 0f
            var imag = 0f
            
            for (n in signal.indices) {
                val angle = -2 * PI * k * n / nFFT
                real += signal[n] * cos(angle).toFloat()
                imag += signal[n] * sin(angle).toFloat()
            }
            
            magnitudes[k] = sqrt(real * real + imag * imag)
        }
        
        return magnitudes
    }

    /**
     * Convert linear frequency to Mel scale
     */
    private fun toMelScale(fftMag: FloatArray): FloatArray {
        val melBins = FloatArray(nMels)
        val freqStep = sampleRate.toFloat() / nFFT
        
        for (melBin in 0 until nMels) {
            val melFreq = melBin * (sampleRate / 2f) / nMels
            val freqBin = (melFreq / freqStep).toInt().coerceIn(0, fftMag.size - 1)
            melBins[melBin] = fftMag[freqBin]
        }
        
        // Apply log scaling (dB scale)
        return melBins.map { ln(maxOf(it, 1e-10f)) }.toFloatArray()
    }

    /**
     * Run model inference
     */
    private fun runInference(melSpectrogram: Array<FloatArray>): Float {
        interpreter ?: return 0f
        
        // Prepare input tensor
        val inputBuffer = ByteBuffer.allocateDirect(4 * nMels * melSpectrogram[0].size)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        for (timeStep in melSpectrogram[0].indices) {
            for (melBin in 0 until nMels) {
                inputBuffer.putFloat(melSpectrogram[melBin][timeStep])
            }
        }
        
        // Prepare output tensor
        val outputBuffer = ByteBuffer.allocateDirect(4 * 3) // 3 classes: distress, neutral, other
        outputBuffer.order(ByteOrder.nativeOrder())
        
        // Run inference
        try {
            interpreter?.run(inputBuffer, outputBuffer)
            
            outputBuffer.rewind()
            val distressProb = outputBuffer.float
            
            return distressProb
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            return 0f
        }
    }

    /**
     * Post-processing: combine model output with audio features
     */
    private fun postProcess(modelScore: Float, audioBuffer: FloatArray): Float {
        // Additional heuristics for distress detection
        
        // 1. Energy level (screams are loud)
        val energy = audioBuffer.map { it * it }.average().toFloat()
        val energyBonus = if (energy > 0.1f) 0.1f else 0f
        
        // 2. Zero-crossing rate (distress sounds have high ZCR)
        val zcr = zeroCrossingRate(audioBuffer)
        val zcrBonus = if (zcr > 0.15f) 0.1f else 0f
        
        // Combine
        return (modelScore + energyBonus + zcrBonus).coerceIn(0f, 1f)
    }

    /**
     * Zero-crossing rate (how often signal changes sign)
     * High for noisy/distressed sounds
     */
    private fun zeroCrossingRate(signal: FloatArray): Float {
        var crossings = 0
        for (i in 1 until signal.size) {
            if ((signal[i] >= 0 && signal[i - 1] < 0) || 
                (signal[i] < 0 && signal[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / signal.size
    }

    /**
     * Load TFLite model from assets
     */
    private fun loadModelFile(): ByteBuffer {
        return try {
            FileUtil.loadMappedFile(context, MODEL_NAME)
        } catch (e: Exception) {
            // Model not found - create dummy buffer for testing
            Log.w(TAG, "Model file not found, using dummy model")
            ByteBuffer.allocateDirect(1024)
        }
    }

    /**
     * Get current ambient noise level (in dB)
     */
    fun getAmbientNoiseLevel(): Float {
        if (!isRecording || audioRecord == null) return 0f
        
        val buffer = ShortArray(1024)
        val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
        
        if (read > 0) {
            val rms = sqrt(buffer.map { (it * it).toDouble() }.average()).toFloat()
            return 20 * log10(rms / 32768f + 1e-10f) + 90 // Convert to dB
        }
        
        return 0f
    }

    /**
     * Clean up resources
     */
    fun release() {
        stopMonitoring()
        interpreter?.close()
        interpreter = null
    }
}
