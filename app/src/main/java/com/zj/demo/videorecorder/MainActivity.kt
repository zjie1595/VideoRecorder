package com.zj.demo.videorecorder

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.ToastUtils
import com.zj.demo.videorecorder.databinding.ActivityMainBinding
import java.io.File
import java.nio.ByteBuffer
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var recording: Recording? = null

    private lateinit var videoCapture: VideoCapture<Recorder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PermissionUtils.permission(
            android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO
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
            val preview = Preview.Builder().build()
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
            preview.setSurfaceProvider(binding.preview.surfaceProvider)

            // 配置视频拍摄用例
            val recorder = Recorder.Builder().build()
            val videoCapture = VideoCapture.withOutput(recorder)
            // 绑定使用用例
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    fun captureVideo(view: View) {
        val currentRecording = recording
        if (currentRecording != null) {
            currentRecording.stop()
            recording = null
            return
        }
        // 输出
        val name = "${UUID.randomUUID()}.mp4"
        val outputFile = File(filesDir, name)
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        recording =
            videoCapture.output.prepareRecording(this, outputOptions).withAudioEnabled().start(
                ContextCompat.getMainExecutor(this)
            ) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.videoCaptureButton.apply {
                            text = "停止录制"
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (recordEvent.hasError().not()) {
                            ToastUtils.showLong("视频录制成功: ${outputFile.absolutePath}")
                            onVideoRecorded(outputFile)
                        } else {
                            recording?.close()
                            recording = null
                        }
                        binding.videoCaptureButton.apply {
                            text = "开始录制"
                            isEnabled = true
                        }
                    }
                }
            }
    }

    private fun onVideoRecorded(videoFile: File) {
        val outputFile = File(filesDir, "video_with_bgm.mp4")
        val audioFile = findBgmFile()
        addAudioToVideo(videoFile.absolutePath, audioFile.absolutePath, outputFile)
    }

    private fun findBgmFile(): File {
        val bgm = File(filesDir, "bgm.mp3")
        if (bgm.exists()) {
            return bgm
        }
        bgm.outputStream().use {
            it.write(resources.openRawResource(R.raw.bgm).readBytes())
        }
        return bgm
    }

    @SuppressLint("WrongConstant")
    private fun addAudioToVideo(videoPath: String, audioPath: String, outputFile: File) {
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoPath)
        val videoTrackIndex = getTrackIndex(videoExtractor, "video/")
        videoExtractor.selectTrack(videoTrackIndex)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(audioPath)
        val audioTrackIndex = getTrackIndex(audioExtractor, "audio/")
        audioExtractor.selectTrack(audioTrackIndex)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

        val mediaMuxer =
            MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val videoTrack = mediaMuxer.addTrack(videoFormat)
        val audioTrack = mediaMuxer.addTrack(audioFormat)
        mediaMuxer.start()

        val buffer = ByteBuffer.allocate(1024 * 1024)
        val videoBufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val sampleSize = videoExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) {
                break
            }
            videoBufferInfo.offset = 0
            videoBufferInfo.size = sampleSize
            videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
            videoBufferInfo.flags = videoExtractor.sampleFlags
            mediaMuxer.writeSampleData(videoTrack, buffer, videoBufferInfo)
            videoExtractor.advance()
        }

        val audioBufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val sampleSize = audioExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) {
                break
            }
            audioBufferInfo.offset = 0
            audioBufferInfo.size = sampleSize
            audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
            audioBufferInfo.flags = audioExtractor.sampleFlags
            mediaMuxer.writeSampleData(audioTrack, buffer, audioBufferInfo)
            audioExtractor.advance()
        }

        videoExtractor.release()
        audioExtractor.release()
        mediaMuxer.stop()
        mediaMuxer.release()

        // 重命名输出文件为原始视频文件的名称
        val outputFileRenamed = File(videoPath)
        outputFile.renameTo(outputFileRenamed)
    }

    private fun getTrackIndex(extractor: MediaExtractor, mimeType: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val trackMimeType = format.getString(MediaFormat.KEY_MIME)
            if (trackMimeType?.startsWith(mimeType) == true) {
                return i
            }
        }
        return -1
    }

}