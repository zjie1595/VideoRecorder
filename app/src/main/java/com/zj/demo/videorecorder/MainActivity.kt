package com.zj.demo.videorecorder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.PermissionUtils
import com.zj.demo.videorecorder.databinding.ActivityMainBinding
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PermissionUtils.permission(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        ).callback { isAllGranted, _, _, _ ->
            if (isAllGranted) {
                startCamera()
            }
        }.request()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            // 配置预览用例
            val cameraProvider = future.get()
            val preview = Preview.Builder()
                .build()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()
            preview.setSurfaceProvider(binding.preview.surfaceProvider)

            // 配置视频拍摄用例
            val recorder = Recorder.Builder()
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            // 输出
            val name = "${UUID.randomUUID()}.mp4"
            val outputOptions = FileOutputOptions.Builder(File(filesDir, name)).build()
//            videoCapture.output.prepareRecording(this, outputOptions)
//                .withAudioEnabled()

            // 绑定使用用例
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
        }, ContextCompat.getMainExecutor(this))
    }
}