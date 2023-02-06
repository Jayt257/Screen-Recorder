package com.jaytaravia.screenrecorder


import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import android.widget.VideoView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE = 1000
    private val REQUEST_PERMISSION = 1001
    private lateinit var mediaProjectionManager : MediaProjectionManager
    private var mediaProjection : MediaProjection?= null
    private var virtualDisplay : VirtualDisplay? = null
    private lateinit var mediaProjectionCallBack : MediaProjectionCallBack

    private var mScreenDensity :Int? = null
    private var DISPLAY_WIDTH = 720
    private var DISPLAY_HEIGHT = 1280

    private var mediaRecorder : MediaRecorder? = null
    private lateinit var toggleBtn : FloatingActionButton

    var isChecked = false

    private lateinit var videoView: VideoView
    private var videoUri :String =""
    private val ORIENTATIONS = SparseIntArray()

    init {
        ORIENTATIONS.append(Surface.ROTATION_0,90)
        ORIENTATIONS.append(Surface.ROTATION_90,0)
        ORIENTATIONS.append(Surface.ROTATION_180,270)
        ORIENTATIONS.append(Surface.ROTATION_270,180)

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        mediaRecorder = MediaRecorder()
        mediaProjectionManager= getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        videoView = findViewById(R.id.videoView)
        toggleBtn = findViewById(R.id.toggleBtn)

        toggleBtn.setOnClickListener {
            if(
                ContextCompat.checkSelfPermission(
                    this,android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) + ContextCompat.checkSelfPermission(
                    this,android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ){
                isChecked = false
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.RECORD_AUDIO
                    ),REQUEST_PERMISSION
                )
            }else{
                toggleScreenShare(toggleBtn)
            }
        }

    }

    private fun toggleScreenShare(v: FloatingActionButton?) {
        if(!isChecked){
            initRecorder()
            recordScreen()
            isChecked = true
            toggleBtn.setImageResource(R.drawable.ic_stop)
        }
        else{
            try{
                mediaRecorder!!.stop()
                mediaRecorder!!.reset()
                stopRecordingScreen()
            }
            catch(e:Exception){
                e.printStackTrace()
            }
            //Play in video view
            videoView.visibility= View.VISIBLE
            videoView.setVideoURI(Uri.parse(videoUri))
            videoView.start()
            isChecked = false
            toggleBtn.setImageResource(R.drawable.ic_video)

        }
    }
    private fun initRecorder() {
        try{
            val recordingFile = ("ScreenREC${System.currentTimeMillis()}.mp4")
            mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)

            val newPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val folder = File(newPath, "MyScreenREC/")
            if (!folder.exists()){
                folder.mkdirs()
            }
            val file1 = File(folder,recordingFile)
            videoUri = file1.absolutePath

            mediaRecorder!!.setOutputFile(videoUri)
            mediaRecorder!!.setVideoSize(DISPLAY_WIDTH,DISPLAY_HEIGHT)
            mediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

            mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mediaRecorder!!.setVideoEncodingBitRate(512*1000)
            mediaRecorder!!.setVideoFrameRate(30)

            val rotation = windowManager.defaultDisplay.rotation
            val orientation = ORIENTATIONS.get(rotation + 90)

            mediaRecorder!!.setOrientationHint(orientation)
            mediaRecorder!!.prepare()
        }
        catch (e:Exception){
            e.printStackTrace()
        }
    }


    private fun recordScreen() {

        if(mediaProjection == null){
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent()
                ,REQUEST_CODE)
        }
        virtualDisplay = createVirtualDisplay()
        try{
            mediaRecorder!!.start()
        }catch(e:Exception){
            e.printStackTrace()
        }

    }

    private fun createVirtualDisplay(): VirtualDisplay? {

        return mediaProjection?.createVirtualDisplay(
            "MainActivity",DISPLAY_WIDTH,DISPLAY_HEIGHT,
            mScreenDensity!!,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface,null,null
        )

    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode != REQUEST_CODE){
            Toast.makeText(this,"Unk Error",Toast.LENGTH_LONG).show()
            return
        }
        if(resultCode != RESULT_OK){
            Toast.makeText(this,"Permission denied",Toast.LENGTH_LONG).show()
            isChecked = false
            return
        }
        mediaProjectionCallBack = MediaProjectionCallBack(
            mediaRecorder!!,mediaProjection
        )
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode,data!!)

        mediaProjection!!.registerCallback(mediaProjectionCallBack, null)
        virtualDisplay = createVirtualDisplay()
        try{
            mediaRecorder!!.start()
        }
        catch (e:Exception){
            e.printStackTrace()
        }

    }

    private fun stopRecordingScreen() {
        if(virtualDisplay == null)
            return
        virtualDisplay!!.release()
        destroMediaProjection()
    }

    private fun destroMediaProjection() {

        if(mediaProjection != null){
            mediaProjection!!.unregisterCallback(mediaProjectionCallBack)
            mediaProjection!!.stop()
            mediaProjection = null
        }

    }

    inner class MediaProjectionCallBack(
        var mediaRecord:MediaRecorder,
        var mediaProjection:MediaProjection?
    ):MediaProjection.Callback()
    {
        override fun onStop() {
            if(isChecked){
                isChecked = false
                mediaRecord.stop()
                mediaRecord.reset()
            }
            mediaProjection = null
            stopRecordingScreen()
            super.onStop()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            REQUEST_PERMISSION ->{
                if(grantResults.size > 0
                    && grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED){
                    toggleScreenShare(toggleBtn)
                }
                else{
                    isChecked = false
                    ActivityCompat.requestPermissions(
                        this, arrayOf(
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,android.Manifest.permission.RECORD_AUDIO
                        ),REQUEST_PERMISSION
                    )
                }
            }
        }
    }
}


