
package com.aryan.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.widget.*
import android.view.Gravity
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var tts: TextToSpeech

    private var speech: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var clapRunning = false
    private var voiceWakeRunning = false
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        buildUI()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }
    }

    private fun buildUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 24)
            setBackgroundColor(0xFF050B16.toInt())
        }

        val title = TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(0xFF45B8FF.toInt())
        }

        status = TextView(this).apply {
            text = "SYSTEM STANDBY"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(0xFF65D6FF.toInt())
        }

        transcript = TextView(this).apply {
            text = "Welcome back. JARVIS is ready."
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(-1)
            setPadding(12, 30, 12, 30)
        }

        fun addButton(name: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                text = name
                setOnClickListener { action() }
            })
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(transcript)

        addButton("TALK TO JARVIS") {
            startCommandListening()
        }

        addButton("DOUBLE CLAP WAKE") {
            startClapWake()
        }

        addButton("HEY JARVIS WAKE") {
            startVoiceWake()
        }

        addButton("STOP LISTENING") {
            stopWake()
        }

        addButton("TEST VOICE") {
            speak("At your service. How may I assist you?")
        }

        setContentView(layout)
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val language = tts.setLanguage(Locale.UK)

            ttsReady =
                language != TextToSpeech.LANG_MISSING_DATA &&
                language != TextToSpeech.LANG_NOT_SUPPORTED

            tts.setSpeechRate(0.88f)
            tts.setPitch(0.78f)
        }
    }

    private fun speak(message: String) {
        transcript.text = message
        status.text = "JARVIS RESPONDING"

        if (ttsReady) {
            tts.speak(
                message,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "JARVIS"
            )
        }
    }

    private fun startCommandListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
            return
        }

        stopWake()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition is unavailable.")
            return
        }

        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)

        speech?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "LISTENING..."
                }

                override fun onBeginningOfSpeech() {
                    status.text = "HEARING YOU..."
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    status.text = "STANDBY"
                    transcript.text = "Please try again."
                }

                override fun onResults(results: Bundle?) {
                    val command = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull()

                    if (command != null) {
                        handleCommand(command)
                    }
                }

                override fun onPartialResults(results: Bundle?) {}
                override fun onEvent(type: Int, params: Bundle?) {}
            }
        )

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "en-IN"
            )
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Speak to JARVIS"
            )
        }

        speech?.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale.ROOT)

        when {

            command.contains("open youtube") -> {
                speak("Opening YouTube.")
                openApp("com.google.android.youtube")
            }

            command.contains("open settings") -> {
                speak("Opening settings.")
                startActivity(
                    Intent(android.provider.Settings.ACTION_SETTINGS)
                )
            }

            command.contains("volume") -> {
                val number = Regex("(\\d{1,3})")
                    .find(command)?.value?.toIntOrNull()

                if (number != null) {
                    val audio = getSystemService(AUDIO_SERVICE)
                            as android.media.AudioManager

                    val max = audio.getStreamMaxVolume(
                        android.media.AudioManager.STREAM_MUSIC
                    )

                    audio.setStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        number.coerceIn(0, 100) * max / 100,
                        0
                    )

                    speak("Volume adjusted.")
                }
            }

            command.contains("take a note") ||
            command.startsWith("note ") -> {

                val note = raw.substringAfter("note", "").trim()

                val prefs = getSharedPreferences(
                    "jarvis_notes", MODE_PRIVATE
                )

                val old = prefs.getString("notes", "") ?: ""

                prefs.edit()
                    .putString("notes", old + "\n" + note)
                    .apply()

                speak("Your note has been saved.")
            }

            command.contains("show notes") -> {
                val notes = getSharedPreferences(
                    "jarvis_notes", MODE_PRIVATE
                ).getString("notes", "")

                speak(notes ?: "No notes found.")
            }

            command.contains("time") -> {
                val time = java.text.SimpleDateFormat(
                    "h:mm a", Locale.getDefault()
                ).format(java.util.Date())

                speak("The time is $time.")
            }

            command.contains("hello") ||
            command.contains("hey jarvis") -> {
                speak("Hello. I'm online and ready.")
            }

            else -> {
                speak("I can handle basic commands. Advanced AI is not connected yet.")
            }
        }
    }

    private fun openApp(packageName: String) {
        val intent = packageManager
            .getLaunchIntentForPackage(packageName)

        if (intent != null) {
            startActivity(intent)
        } else {
            speak("Application not installed.")
        }
    }

    private fun startClapWake() {
        stopWake()

        clapRunning = true

        status.text = "CLAP DETECTION ACTIVE"

        thread {
            val rate = 16000

            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBuffer <= 0) {
                handler.post { speak("Microphone initialization failed.") }
                clapRunning = false
                return@thread
            }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )

            try {
                recorder.startRecording()

                val buffer = ShortArray(512)
                var lastClap = 0L
                var count = 0

                while (clapRunning) {
                    val size = recorder.read(
                        buffer, 0, buffer.size
                    )

                    if (size > 0) {
                        var peak = 0

                        for (i in 0 until size) {
                            peak = maxOf(
                                peak,
                                abs(buffer[i].toInt())
                            )
                        }

                        val now = System.currentTimeMillis()

                        if (peak > 12500 &&
                            now - lastClap > 180) {

                            if (now - lastClap < 900) {
                                count++
                            } else {
                                count = 1
                            }

                            lastClap = now

                            if (count >= 2) {
                                count = 0

                                handler.post {
                                    stopClap()
                                    speak("Yes?")
                                    handler.postDelayed({
                                        startCommandListening()
                                    }, 450)
                                }
                            }
                        }
                    }
                }

            } catch (_: Exception) {
                handler.post {
                    status.text = "CLAP DETECTOR STOPPED"
                }
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
            }
        }
    }

    private fun stopClap() {
        clapRunning = false
    }

    private fun startVoiceWake() {
        stopWake()
        voiceWakeRunning = true
        listenForWake()
    }

    private fun listenForWake() {
        if (!voiceWakeRunning) return

        speech?.destroy()
        speech = SpeechRecognizer.createSpeechRecognizer(this)

        speech?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "WAITING FOR HEY JARVIS"
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(results: Bundle?) {}
                override fun onEvent(type: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    if (voiceWakeRunning) {
                        handler.postDelayed({
                            listenForWake()
                        }, 700)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val heard = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull()
                        ?.lowercase(Locale.ROOT) ?: ""

                    if (heard.contains("hey jarvis")) {
                        voiceWakeRunning = false
                        speak("I'm listening.")

                        handler.postDelayed({
                            startCommandListening()
                        }, 500)

                    } else if (voiceWakeRunning) {
                        handler.postDelayed({
                            listenForWake()
                        }, 300)
                    }
                }
            }
        )

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "en-IN"
            )
        }

        speech?.startListening(intent)
    }

    private fun stopWake() {
        clapRunning = false
        voiceWakeRunning = false

        try {
            speech?.cancel()
        } catch (_: Exception) {}

        status.text = "SYSTEM STANDBY"
    }

    override fun onPause() {
        super.onPause()
        stopWake()
    }

    override fun onDestroy() {
        stopWake()
        speech?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
