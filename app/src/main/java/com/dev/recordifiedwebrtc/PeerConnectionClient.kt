package com.dev.recordifiedwebrtc

import android.opengl.EGLContext
import android.os.Process
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceGatheringState
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnection.SignalingState
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoCapturer
import org.webrtc.VideoCapturerAndroid
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.net.URISyntaxException
import java.util.LinkedList

class PeerConnectionClient(
    private val roomId: String,
    private val mListener: RtcListener,
    host: String?,
    private val pcParams: PeerConnectionParameters,
    mEGLcontext: EGLContext?
) {
    private val factory: PeerConnectionFactory
    private val iceServers = LinkedList<IceServer>()
    private val pcConstraints = MediaConstraints()
    private var localMS: MediaStream? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer : VideoCapturer?=null
    private var socketClient: Socket? = null
    private var peer: Peer? = null
    private var isUsingFrontCamera = true

    /**
     * Implement this interface to be notified of events.
     */
    interface RtcListener {
        fun onStatusChanged(newStatus: String?)
        fun onLocalStream(localStream: MediaStream?)
        fun onAddRemoteStream(remoteStream: MediaStream?)
        fun onRemoveRemoteStream()
        fun onMessage(message: String?)
    }

    private inner class MessageHandler {
        val onConnect = Emitter.Listener {
            val obj = JSONObject()
            try {
                obj.put("roomId", roomId)
                socketClient!!.emit("join room", obj)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val onDisconnect =
            Emitter.Listener { args: Array<Any?>? -> Log.d(TAG, "Socket disconnected") }
        val onMessage = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            try {
                val message = data.getString("message")
                mListener.onMessage(message)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val onNewUserJoined = Emitter.Listener {
            peer = Peer()
            peer!!.pc.createOffer(peer, pcConstraints)
        }
        val onOffer = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            peer = Peer()
            try {
                val offer = data.getJSONObject("offer")
                val sdp = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(offer.getString("type")),
                    offer.getString("sdp")
                )
                peer!!.pc.setRemoteDescription(peer, sdp)
                peer!!.pc.createAnswer(peer, pcConstraints)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val onAnswer = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            try {
                val answer = data.getJSONObject("answer")
                val sdp = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(answer.getString("type")),
                    answer.getString("sdp")
                )
                peer!!.pc.setRemoteDescription(peer, sdp)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val onNewIceCandidate = Emitter.Listener { args ->
            val data = args[0] as JSONObject
            try {
                val iceCandidate = data.getJSONObject("iceCandidate")
                if (peer!!.pc.remoteDescription != null) {
                    val candidate = IceCandidate(
                        iceCandidate.getString("sdpMid"),
                        iceCandidate.getInt("sdpMLineIndex"),
                        iceCandidate.getString("sdpCandidate")
                    )
                    peer!!.pc.addIceCandidate(candidate)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private inner class Peer : SdpObserver, PeerConnection.Observer {
        val pc: PeerConnection
        override fun onCreateSuccess(sdp: SessionDescription) {
            try {
                pc.setLocalDescription(this@Peer, sdp)
                val payload = JSONObject()
                val desc = JSONObject()
                desc.put(
                    "type",
                    sdp.type.canonicalForm()
                ) // sdp.type.canonicalForm() returns: 'offer' or 'answer'
                desc.put("sdp", sdp.description)
                payload.put(sdp.type.canonicalForm(), desc)
                payload.put("roomId", roomId)
                socketClient!!.emit(sdp.type.canonicalForm(), payload)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        override fun onSetSuccess() {}
        override fun onCreateFailure(s: String) {}
        override fun onSetFailure(s: String) {}
        override fun onSignalingChange(signalingState: SignalingState) {}
        override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
            if (iceConnectionState == IceConnectionState.DISCONNECTED) {
                mListener.onStatusChanged("DISCONNECTED")
                mListener.onRemoveRemoteStream()
            }
            if (iceConnectionState == IceConnectionState.CONNECTED) {
                Log.d(TAG, "Peers connected")
                mListener.onStatusChanged("CONNECTED")
            }
        }

        override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {}
        override fun onIceCandidate(candidate: IceCandidate) {
            try {
                val payload = JSONObject()
                val iceCandidate = JSONObject()
                iceCandidate.put("sdpMLineIndex", candidate.sdpMLineIndex)
                iceCandidate.put("sdpMid", candidate.sdpMid)
                iceCandidate.put("sdpCandidate", candidate.sdp)
                payload.put("iceCandidate", iceCandidate)
                payload.put("roomId", roomId)
                socketClient!!.emit("new ice candidate", payload)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        override fun onAddStream(mediaStream: MediaStream) {
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream)
        }

        override fun onRemoveStream(mediaStream: MediaStream) {
            mListener.onRemoveRemoteStream()
        }

        override fun onDataChannel(dataChannel: DataChannel) {}
        override fun onRenegotiationNeeded() {}

        init {
            pc = factory.createPeerConnection(iceServers, pcConstraints, this)
            pc.addStream(localMS) //, new MediaConstraints()
            mListener.onStatusChanged("CONNECTING")
        }
    }

    init {
        PeerConnectionFactory.initializeAndroidGlobals(
            mListener, true, true,
            pcParams.videoCodecHwAcceleration, mEGLcontext
        )
        factory = PeerConnectionFactory()
        val messageHandler = MessageHandler()
        try {
            socketClient = IO.socket(host)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
        socketClient!!.on(Socket.EVENT_CONNECT_ERROR) { args -> // Handle connection error
            val e = args[0] as Exception
            e.printStackTrace()
        }
        socketClient!!.on(Socket.EVENT_CONNECT, messageHandler.onConnect)
        socketClient!!.on("new user joined", messageHandler.onNewUserJoined)
        socketClient!!.on("offer", messageHandler.onOffer)
        socketClient!!.on("answer", messageHandler.onAnswer)
        socketClient!!.on("new ice candidate", messageHandler.onNewIceCandidate)
        socketClient!!.on(Socket.EVENT_DISCONNECT, messageHandler.onDisconnect)
        socketClient!!.on("message", messageHandler.onMessage)
        socketClient!!.connect()

        iceServers.add(IceServer("stun:192.168.1.40:3478"))
//        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        /*iceServers.add(IceServer("stun:iphone-stun.strato-iphone.de:3478"))
        iceServers.add(IceServer("stun:stun.relay.metered.ca:80"))
        iceServers.add(
            IceServer(
                "turn:global.relay.metered.ca:80",
                "68fe1fd8a1b97eaae5107dfa",
                "LMeZeSRnfD8m/JQ1"
            )
        )
        iceServers.add(
            IceServer(
                "turn:global.relay.metered.ca:80?transport=tcp",
                "68fe1fd8a1b97eaae5107dfa",
                "LMeZeSRnfD8m/JQ1"
            )
        )
        iceServers.add(
            IceServer(
                "turn:global.relay.metered.ca:443",
                "68fe1fd8a1b97eaae5107dfa",
                "LMeZeSRnfD8m/JQ1"
            )
        )
        iceServers.add(
            IceServer(
                "turns:global.relay.metered.ca:443?transport=tcp",
                "68fe1fd8a1b97eaae5107dfa",
                "LMeZeSRnfD8m/JQ1"
            )
        )*/
        pcConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        pcConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        pcConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
    }

    /**
     * Call this method in Activity.onPause()
     */
    fun onPause() {
        if (videoSource != null) videoSource!!.stop()
    }

    /**
     * Call this method in Activity.onResume()
     */
    fun onResume() {
        if (videoSource != null) videoSource!!.restart()
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    fun onDestroy() {
        Process.killProcess(Process.myPid()) // use this code as videoSource.dispose will cause app crash (but if comment that line we cannot start the video source again as access to camera is not released)

//        // Ignore this section
//        if (videoSource != null) {
//            videoSource?.dispose();
//        }
        // Ignore the section above

//        factory.dispose();
//        socketClient?.disconnect();
//        socketClient?.close();
    }

    /**
     * Start the client.
     *
     *
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     */
    fun start() {
        setCamera()
    }

    private fun setCamera() {

        val cameraName = if(isUsingFrontCamera) "front_camera_name" else "back_camera_name"

        videoCapturer = createVideoCapturer(cameraName)

        videoCapturer
        localMS = factory.createLocalMediaStream("LOCAL_MS")
        if (pcParams.videoCallEnabled) {
            val videoConstraints = MediaConstraints()
            videoConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                    "maxHeight",
                    pcParams.videoHeight.toString()
                )
            )
            videoConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                    "maxWidth",
                    pcParams.videoWidth.toString()
                )
            )
            videoConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                    "maxFrameRate",
                    pcParams.videoFps.toString()
                )
            )
            videoConstraints.mandatory.add(
                MediaConstraints.KeyValuePair(
                    "minFrameRate",
                    pcParams.videoFps.toString()
                )
            )
            videoSource = factory.createVideoSource(videoCapturer, videoConstraints)
            localVideoTrack = factory.createVideoTrack("LOCAL_MS_VS", videoSource)
            localMS?.addTrack(localVideoTrack)
            
        }
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("LOCAL_MS_AT", audioSource)
        localMS?.addTrack(localAudioTrack)
        mListener.onLocalStream(localMS)

    }

    private fun createVideoCapturer(cameraName: String): VideoCapturer? {
        if (cameraName=="front_camera_name"){
            return VideoCapturerAndroid.create(VideoCapturerAndroid.getNameOfFrontFacingDevice())
        }else{
            return VideoCapturerAndroid.create(VideoCapturerAndroid.getNameOfBackFacingDevice())
        }

    }

    fun toggleAudio(mute: Boolean) {
        localAudioTrack?.setEnabled(mute)
    }

    fun toggleVideo(cameraPause: Boolean) {
        localVideoTrack?.setEnabled(cameraPause)
    }

    fun switchCamera(frontCamera: Boolean) {
        isUsingFrontCamera = frontCamera
        videoSource?.stop()
        videoCapturer?.dispose()
        setCamera()
    }

    companion object {
        private val TAG = PeerConnectionClient::class.java.getCanonicalName()
    }
}