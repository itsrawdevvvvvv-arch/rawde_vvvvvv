package com.rawedit.app.render

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

class SurfaceEncoder(
    outputVideoFile: File,
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val fps: Int = 30,
    durationSec: Double
) {
    private val encoder: MediaCodec
    private val inputSurface: InputSurface
    private val muxer: MediaMuxer
    private val bufferInfo = MediaCodec.BufferInfo()

    private var trackIndex = -1
    private var muxerStarted = false

    init {
        val bitrate = chooseBitrate(durationSec)
        encoder = createAndConfigureCodec(bitrate)
        inputSurface = InputSurface(encoder.createInputSurface())
        encoder.start()
        muxer = MediaMuxer(outputVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun createAndConfigureCodec(bitrate: Int): MediaCodec {
        val candidates = listOf(
            // Preferred: High Profile + VBR
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            },
            // Fallback: Standard surface format without strict profile/level flags
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
        )

        var lastError: Throwable? = null
        for (format in candidates) {
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                return codec
            } catch (t: Throwable) {
                lastError = t
                runCatching { codec.release() }
            }
        }
        throw IllegalStateException("Could not configure H.264 encoder for ${width}x$height", lastError)
    }

    fun drawBitmapAndSubmit(bitmap: Bitmap, ptsNs: Long) {
        inputSurface.drawBitmapAndSubmit(bitmap, ptsNs)
    }

    fun drainEncoder(endOfStream: Boolean = false) {
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

        val timeoutUs = 10_000L
        while (true) {
            val outputStatus = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "Format changed twice" }
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outputStatus >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputStatus)
                        ?: error("encoderOutputBuffer $outputStatus was null")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        check(muxerStarted) { "Muxer hasn't started" }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputStatus, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    fun finish() {
        drainEncoder(endOfStream = true)
        release()
    }

    fun release() {
        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        runCatching { inputSurface.release() }
        runCatching {
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }

    companion object {
        fun chooseBitrate(durationSec: Double): Int {
            return when {
                durationSec <= 60.0 -> 5_000_000
                durationSec <= 180.0 -> 3_500_000
                else -> 2_800_000
            }
        }
    }
}
