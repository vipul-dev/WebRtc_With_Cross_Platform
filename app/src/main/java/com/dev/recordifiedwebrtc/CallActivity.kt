package com.dev.recordifiedwebrtc

import android.content.DialogInterface
import android.graphics.Point
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dev.recordifiedwebrtc.databinding.ActivityCallBinding
import org.webrtc.MediaStream
import org.webrtc.VideoRenderer
import org.webrtc.VideoRendererGui

class CallActivity : AppCompatActivity(), PeerConnectionClient.RtcListener {

    private val VIDEO_CODEC_VP9 = "VP9"
    private val AUDIO_CODEC_OPUS = "opus"

    // Local preview screen position before call is connected.
    private val LOCAL_X_CONNECTING = 0
    private val LOCAL_Y_CONNECTING = 0
    private val LOCAL_WIDTH_CONNECTING = 100
    private val LOCAL_HEIGHT_CONNECTING = 100

    // Local preview screen position after call is connected.
    private val LOCAL_X_CONNECTED = 72
    private val LOCAL_Y_CONNECTED = 72
    private val LOCAL_WIDTH_CONNECTED = 25
    private val LOCAL_HEIGHT_CONNECTED = 25

    // Remote video screen position
    private val REMOTE_X = 0
    private val REMOTE_Y = 0
    private val REMOTE_WIDTH = 100
    private val REMOTE_HEIGHT = 100
    private val scalingType: VideoRendererGui.ScalingType =
        VideoRendererGui.ScalingType.SCALE_ASPECT_FILL
    private var vsv: GLSurfaceView? = null
    private var localRender: VideoRenderer.Callbacks? = null
    private var remoteRender: VideoRenderer.Callbacks? = null
    private var peerConnectionClient: PeerConnectionClient? = null
    private var mSocketAddress: String? = null
    private var roomId: String? = null
    private val rtcAudioManager by lazy {
        RTCAudioManager.create(this)
    }
    private var isSpeakerMode = true
    private var isMute = true
    private var isFrontCamera = true
    private var isCameraPause = true

    private val binding by lazy {
        ActivityCallBinding.inflate(layoutInflater)
    }

    @Suppress("INACCESSIBLE_TYPE")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        window.addFlags( //                LayoutParams.FLAG_FULLSCREEN
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(binding.root)

        setListener()
        mSocketAddress = getString(R.string.serverAddress)
        vsv = findViewById(R.id.glview_call)
        vsv?.preserveEGLContextOnPause = true
        vsv?.keepScreenOn = true
        VideoRendererGui.setView(vsv, ::init)


        // local and remote render
        remoteRender = VideoRendererGui.create(
            REMOTE_X, REMOTE_Y,
            REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false
        )
        localRender = VideoRendererGui.create(
            LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
            LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, false
        )
        val intent = intent
        roomId = intent.getStringExtra(MainActivity().EXTRA_MESSAGE)
//        setTitle("RoomID: $roomId") // set action bar title (this method is built-in provided by class AppCompatActivity)
    }

    private fun setListener() {
        runOnUiThread {
            binding.apply {
                endCallButton.setOnClickListener { finish() }

                switchCameraButton.setOnClickListener {
                    isFrontCamera = !isFrontCamera
                    peerConnectionClient?.switchCamera(isFrontCamera)
                }

                micButton.setOnClickListener {
                    if (isMute) {
                        isMute = false
                        micButton.setImageResource(R.drawable.ic_baseline_mic_off_24)
                    } else {
                        isMute = true
                        micButton.setImageResource(R.drawable.ic_baseline_mic_24)
                    }
                    peerConnectionClient?.toggleAudio(isMute)
                }

                videoButton.setOnClickListener {
                    if (isCameraPause) {
                        isCameraPause = false
                        videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24)
                    } else {
                        isCameraPause = true
                        videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
                    }

                    peerConnectionClient?.toggleVideo(isCameraPause)
                }

                audioOutputButton.setOnClickListener {
                    if (isSpeakerMode) {
                        isSpeakerMode = false
                        audioOutputButton.setImageResource(R.drawable.ic_baseline_hearing_24)
                        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.EARPIECE)
                    } else {
                        isSpeakerMode = true
                        audioOutputButton.setImageResource(R.drawable.ic_baseline_speaker_up_24)
                        rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
                    }

                }

            }


        }
    }


    private fun init() {
        val displaySize = Point()
        windowManager.defaultDisplay.getSize(displaySize)
        val params = PeerConnectionParameters(
            true,
            false,
            displaySize.x,
            displaySize.y,
            30,
            1,
            VIDEO_CODEC_VP9,
            true,
            1,
            AUDIO_CODEC_OPUS,
            true
        )
        peerConnectionClient = PeerConnectionClient(
            roomId!!,
            this,
            mSocketAddress,
            params,
            VideoRendererGui.getEGLContext()
        )
        peerConnectionClient!!.start()

        runOnUiThread {

            rtcAudioManager.setDefaultAudioDevice(RTCAudioManager.AudioDevice.SPEAKER_PHONE)
        }

    }

    public override fun onPause() {
        super.onPause()
        vsv!!.onPause()
        if (peerConnectionClient != null) {
            peerConnectionClient!!.onPause()
        }
    }

    public override fun onResume() {
        super.onResume()
        vsv!!.onResume()
        if (peerConnectionClient != null) {
            peerConnectionClient!!.onResume()
        }
    }

    public override fun onDestroy() {
        if (peerConnectionClient != null) {
            peerConnectionClient!!.onDestroy()
        }
        super.onDestroy()
    }

    override fun onStatusChanged(newStatus: String?) {
        runOnUiThread {
            if (newStatus == "CONNECTED") {
                binding.remoteViewLoading.visibility = View.GONE
            } else {
                binding.remoteViewLoading.visibility = View.VISIBLE
            }

            Toast.makeText(
                this@CallActivity,
                newStatus,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onMessage(message: String?) {
        runOnUiThread {
            val builder =
                AlertDialog.Builder(this@CallActivity)
            builder.setTitle("Alert")
            builder.setMessage(message)
            builder.setPositiveButton(
                "Close"
            ) { dialog: DialogInterface, id: Int -> dialog.cancel() }
            builder.setNegativeButton(
                "Go back"
            ) { dialog: DialogInterface, id: Int ->
                dialog.cancel()
                finish()
            }
            val alert = builder.create()
            alert.show()
        }
    }

    override fun onLocalStream(localStream: MediaStream?) {
        localStream?.videoTracks?.get(0)?.addRenderer(VideoRenderer(localRender))
    }

    override fun onAddRemoteStream(remoteStream: MediaStream?) {
        remoteStream?.videoTracks?.get(0)?.addRenderer(VideoRenderer(remoteRender))

        // set remoteRender to fullscreen
        VideoRendererGui.update(
            remoteRender,
            REMOTE_X, REMOTE_Y,
            REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false
        )

        // set localRender to be a small, Picture-in-Picture (in bottom right)
        VideoRendererGui.update(
            localRender,
            LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
            LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
            scalingType, false
        )
    }

    override fun onRemoveRemoteStream() {
        // change position of localRender to fullscreen
        VideoRendererGui.update(
            localRender,
            LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
            LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
            scalingType, false
        )
    }

}