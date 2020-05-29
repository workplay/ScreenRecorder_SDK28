package com.example.screenrecorder_sdk28

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import java.io.IOException
import java.nio.ByteBuffer





class MainActivity : AppCompatActivity() {

    private var mInputSurface: Surface? = null
    private lateinit var mEncoder: MediaCodec
    private var mBufferInfo: MediaCodec.BufferInfo? = null
    @Volatile
    private var mIsStopRequested = false
    private var workHanlder: Handler? = null


    private lateinit var tv: TextView
    private lateinit var button: Button
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mMediaProjection: MediaProjection? = null
    private lateinit var mVirtualDisplay: VirtualDisplay
    private lateinit var mSurface: Surface
    private lateinit var mSurfaceView: SurfaceView
    private var mResultCode = 0
    private var mResultData: Intent? = null
    private var mScreenDesity = 0;

    companion object {
        private const val REQUEST_CAPTURE = 1
        var projection: MediaProjection? = null
    }


    private fun createEncoderThread() {
        val encoder = HandlerThread("Encoder")
        encoder.start()
        val looper: Looper = encoder.looper
        workHanlder = Handler(looper)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv = findViewById(R.id.textview)
        button = findViewById(R.id.button)
        mSurfaceView = findViewById(R.id.surface)
        tv.setText("I'm back to work on Android!")

        mSurfaceView = findViewById(R.id.surface)
        mSurface = mSurfaceView.holder.surface

        var metrics = DisplayMetrics()
        this.windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDesity = metrics.densityDpi

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        button.setOnClickListener {
            startScreenCapture()
        }

        createEncoderThread()


    }

    private fun startScreenCapture() {
        if (mMediaProjection != null) {
            setUpVirtualDisplay()
        } else if (mResultCode != 0 && mResultData != null) {
            mMediaProjection = mediaProjectionManager.getMediaProjection(mResultCode, mResultData!!)
            setUpVirtualDisplay()
        } else {
            startActivityForResult(
                    mediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_CAPTURE
            )
        }
    }

    private val MIME_TYPE = "video/avc" // H.264 Advanced Video Coding
    private val FRAME_RATE = 15 // 30fps
    private val IFRAME_INTERVAL = 5 // 5 seconds between I-frames
    private val BIT_RATE = 800000 // 5 seconds between I-frames
    private val width = 480
    private val height = 720


    private fun setUpVirtualDisplay() {
        val surface = createSurface()

        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay("ScreenCapture",
                width, height, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                surface, null, null
        )

    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK) {
                mResultData = data
                mResultCode = resultCode
                mMediaProjection = mediaProjectionManager.getMediaProjection(mResultCode, mResultData!!)
                setUpVirtualDisplay()
                Toast.makeText(this, "Capture Screen Successfully.", Toast.LENGTH_SHORT).show()

            } else {
                projection = null
                Toast.makeText(this, "ERROR Capture Picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class FrameCallbackClass : FrameCallback {
        override fun render(
            info: MediaCodec.BufferInfo?,
            outputBuffer: ByteBuffer?
        ) {
            Log.d("Shiheng-rendering", info.toString())
            Log.d("Shiheng-rendering",outputBuffer.toString())

        }

        override fun formatChange(mediaFormat: MediaFormat?) {
            Log.d("Shiheng", "Format changed.")
        }
    }

    interface FrameCallback {
        fun render(
            info: MediaCodec.BufferInfo?,
            outputBuffer: ByteBuffer?
        )
        fun formatChange(mediaFormat: MediaFormat?)
    }

    private fun createSurface(): Surface? {
        mBufferInfo = MediaCodec.BufferInfo()
        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)

        try {
            mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mEncoder.createInputSurface();
            mEncoder.start();

            workHanlder!!.postDelayed({
                doExtract(mEncoder, FrameCallbackClass())
            }, 1000)
            return mInputSurface!!

        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    val LOG_TAG = "Shiheng"
    val VERBOSE = false


    /**
     * 不断循环获取，直到我们手动结束.同步的方式
     *
     * @param encoder       编码器
     * @param frameCallback 获取的回调
     */
    private fun doExtract(
        encoder: MediaCodec,
        frameCallback: FrameCallback?
    ) {
        val TIMEOUT_USEC = 10000
        var firstInputTimeNsec: Long = -1
        var outputDone = false
        while (!outputDone) {
//            if (VERBOSE) Log.d(TAG, "loop");
            if (mIsStopRequested) {
                Log.d(LOG_TAG, "Stop requested")
                return
            }
            val decoderStatus = encoder.dequeueOutputBuffer(mBufferInfo!!, TIMEOUT_USEC.toLong())
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
//                if (VERBOSE) Log.d(TAG, "no output from decoder available");
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not important for us, since we're using Surface
//                if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                if (VERBOSE) Log.d(
                    LOG_TAG,
                    "decoder output format changed: $newFormat"
                )
                frameCallback?.formatChange(newFormat)
            } else if (decoderStatus < 0) {
                throw RuntimeException(
                    "unexpected result from decoder.dequeueOutputBuffer: " +
                            decoderStatus
                )
            } else { // decoderStatus >= 0
                if (firstInputTimeNsec != 0L) {
                    // Log the delay from the first buffer of input to the first buffer
                    // of output.
                    val nowNsec = System.nanoTime()
                    Log.d(
                        LOG_TAG,
                        "startup lag " + (nowNsec - firstInputTimeNsec) / 1000000.0 + " ms"
                    )
                    firstInputTimeNsec = 0
                }
                if (VERBOSE) Log.d(
                    LOG_TAG, "surface decoder given buffer " + decoderStatus +
                            " (size=" + mBufferInfo!!.size + ")"
                )
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (VERBOSE) Log.d(LOG_TAG, "output EOS")
                    outputDone = true
                }
                val doRender = mBufferInfo!!.size != 0
                if (doRender && frameCallback != null) {
                    val outputBuffer: ByteBuffer? = encoder.getOutputBuffer(decoderStatus)
                    frameCallback.render(mBufferInfo, outputBuffer)
                }
                encoder.releaseOutputBuffer(decoderStatus, doRender)
            }
        }
    }



    @Override
    override fun dispatchTouchEvent(ev: MotionEvent) : Boolean
    {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            Log.d("Touch Screen", ev.toString())
        }
        return super.dispatchTouchEvent(ev);
    }



}

