package com.example.car_control2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val localIp = "192.168.1.50"
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var videoSocket: WebSocketClient
    private lateinit var audioSocket: WebSocketClient
    private lateinit var commandSocket: WebSocketClient

    private lateinit var cameraFeedImageView: ImageView
    private lateinit var micButton: Button
    private lateinit var camCompassUpDown: SeekBar
    private lateinit var camCompassLeftRight: SeekBar

    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val AUDIO_PERMISSION_CODE = 100

    private val sampleRate = 16000
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraFeedImageView = findViewById(R.id.iv_camera_feed)
        camCompassUpDown = findViewById(R.id.camUpDownCompassHud)
        camCompassLeftRight = findViewById(R.id.camLeftRightCompassHud)
        micButton = findViewById(R.id.btn_mic)

        camCompassUpDown.setOnTouchListener { _, _ -> true }
        camCompassUpDown.progress = 150
        camCompassLeftRight.setOnTouchListener { _, _ -> true }
        camCompassLeftRight.progress = 90

        val forward = findViewById<Button>(R.id.btn_forward)
        val backward = findViewById<Button>(R.id.btn_backward)
        val left = findViewById<Button>(R.id.btn_left)
        val right = findViewById<Button>(R.id.btn_right)
        val camUp = findViewById<Button>(R.id.btn_cam_forward)
        val camDown = findViewById<Button>(R.id.btn_cam_backward)
        val camLeft = findViewById<Button>(R.id.btn_cam_left)
        val camRight = findViewById<Button>(R.id.btn_cam_right)

        connectVideoSocket()
        connectAudioSocket()
        connectCommandSocket()

        setupTouchControl(forward, "forward")
        setupTouchControl(backward, "backward")
        setupTouchControl(left, "left")
        setupTouchControl(right, "right")
        setupTouchControl(camUp, "cam_up")
        setupTouchControl(camDown, "cam_down")
        setupTouchControl(camLeft, "cam_left")
        setupTouchControl(camRight, "cam_right")

        micButton.setOnClickListener { toggleMicRecording() }
    }

    private fun setupTouchControl(button: Button, command: String) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sendCommand(command)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sendCommand("stop")
                    true
                }
                else -> false
            }
        }
    }

    private fun connectVideoSocket() {
        val uri = URI("ws://$localIp:8080")
        videoSocket = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("VideoSocket", "Connected")
            }

            override fun onMessage(message: String?) {
                if (message != null && message.startsWith("IMAGE:")) {
                    val imageData = message.substringAfter("IMAGE:")
                    try {
                        val decoded = Base64.decode(imageData, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                        runOnUiThread {
                            cameraFeedImageView.setImageBitmap(bitmap)
                        }
                    } catch (e: Exception) {
                        Log.e("VideoSocket", "Image decoding error: ${e.message}")
                    }
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w("VideoSocket", "Connection closed: $reason")
                reconnect(::connectVideoSocket)
            }

            override fun onError(ex: Exception?) {
                Log.e("VideoSocket", "Error: ${ex?.message}")
            }
        }
        videoSocket.connect()
    }

    private fun connectAudioSocket() {
        val uri = URI("ws://$localIp:8081")
        audioSocket = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("AudioSocket", "Connected")
            }

            override fun onMessage(message: String?) {}
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w("AudioSocket", "Connection closed: $reason")
                reconnect(::connectAudioSocket)
            }

            override fun onError(ex: Exception?) {
                Log.e("AudioSocket", "Error: ${ex?.message}")
            }
        }
        audioSocket.connect()
    }

    private fun connectCommandSocket() {
        val uri = URI("ws://$localIp:8082")
        commandSocket = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                Log.d("CommandSocket", "Connected")
            }

            override fun onMessage(message: String?) {
                if (message != null && message.startsWith("angel_")) {
                    val command = message.substringAfter("angel_")
                    val direction = command.substringBefore(":")
                    val valueStr = command.substringAfter(":")
                    val valueBar = valueStr.toIntOrNull() ?: 0
                    if(direction == "up" || direction == "down"){
                        runOnUiThread {
                            camCompassUpDown.progress = valueBar
                        }
                    } else if (direction == "left" || direction == "right"){
                        runOnUiThread {
                            camCompassLeftRight.progress = valueBar
                        }
                    }
                    Log.d("WebSocket", "Direction: $direction, Value: $valueBar")
                } else {
                    Log.d("WebSocket", "Unprocessable message: $message")
                }
                if (message != null &&
                    !message.startsWith("IMAGE:") &&
                    !message.startsWith("angel:")) {
                    Log.d("WebSocket", "Command or other message: $message")
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                Log.w("CommandSocket", "Connection closed: $reason")
                reconnect(::connectCommandSocket)
            }

            override fun onError(ex: Exception?) {
                Log.e("CommandSocket", "Error: ${ex?.message}")
            }
        }
        commandSocket.connect()
    }

    private fun sendCommand(command: String) {
        if (commandSocket.isOpen) {
            commandSocket.send(command)
        }
    }

    private fun sendAudio(encodedAudio: String) {
        if (audioSocket.isOpen) {
            audioSocket.send("AUDIO:$encodedAudio")
        }
    }

    private fun toggleMicRecording() {
        if (isRecording) {
            stopRecording()
            micButton.setBackgroundResource(R.drawable.microphone_sound_off_14636)
            Toast.makeText(this, "Microphone off", Toast.LENGTH_SHORT).show()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
            } else {
                startRecording()
                micButton.setBackgroundResource(R.drawable.microphone_342)
                Toast.makeText(this, "Microphone on", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return
        audioRecord?.startRecording()
        isRecording = true

        thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readBytes > 0) {
                    val encoded = Base64.encodeToString(buffer, 0, readBytes, Base64.DEFAULT)
                    sendAudio(encoded)
                }
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun reconnect(connectFunc: () -> Unit) {
        handler.postDelayed({ connectFunc() }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        videoSocket.close()
        audioSocket.close()
        commandSocket.close()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == AUDIO_PERMISSION_CODE && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording()
            micButton.setBackgroundResource(R.drawable.microphone_342)
        } else {
            micButton.setBackgroundResource(R.drawable.microphone_sound_off_14636)
        }
    }
}