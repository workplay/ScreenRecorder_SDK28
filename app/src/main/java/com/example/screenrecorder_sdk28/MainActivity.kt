package com.example.screenrecorder_sdk28

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Surface
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {

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

    private fun setUpVirtualDisplay() {
        mVirtualDisplay = mMediaProjection!!.createVirtualDisplay("ScreenCapture",
                mSurfaceView.width, mSurfaceView.height, mScreenDesity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface, null, null
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

}

