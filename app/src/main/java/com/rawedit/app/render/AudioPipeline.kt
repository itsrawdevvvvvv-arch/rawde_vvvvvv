package com.rawedit.app.render

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
object AudioPipeline {

    suspend fun transcodeToAac(
        context: Context,
        sourceAudioFile: File,
        destAacMp4: File,
        cancelled: () -> Boolean
    ) {
        if (destAacMp4.exists()) destAacMp4.delete()
        val deferred = CompletableDeferred<Unit>()
        var transformer: Transformer? = null

        withContext(Dispatchers.Main) {
            if (cancelled()) {
                deferred.cancel()
                return@withContext
            }
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder()
                        .setBitrate(128_000)
                        .build()
                )
                .build()

            val t = Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (!deferred.isCompleted) deferred.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (!deferred.isCompleted) deferred.completeExceptionally(exportException)
                    }
                })
                .build()

            transformer = t
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceAudioFile)))
                .setRemoveVideo(true)
                .build()
            t.start(item, destAacMp4.absolutePath)
        }

        try {
            deferred.await()
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                runCatching { transformer?.cancel() }
            }
            throw t
        }
    }

    suspend fun muxVideoAndAudio(
        videoOnlyMp4: File,
        audioAacMp4: File,
        finalMp4: File,
        targetDurationUs: Long
    ) = withContext(Dispatchers.IO) {
        if (finalMp4.exists()) finalMp4.delete()

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            videoExtractor.setDataSource(videoOnlyMp4.absolutePath)
            val videoSourceTrack = findTrackIndex(videoExtractor, "video/")
            require(videoSourceTrack >= 0) { "Rendered video file has no video track" }
            videoExtractor.selectTrack(videoSourceTrack)
            val videoFormat = videoExtractor.getTrackFormat(videoSourceTrack)

            audioExtractor.setDataSource(audioAacMp4.absolutePath)
            val audioSourceTrack = findTrackIndex(audioExtractor, "audio/")
            val hasAudio = audioSourceTrack >= 0
            if (hasAudio) {
                audioExtractor.selectTrack(audioSourceTrack)
            }

            muxer = MediaMuxer(finalMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = if (hasAudio) {
                muxer.addTrack(audioExtractor.getTrackFormat(audioSourceTrack))
            } else {
                -1
            }

            muxer.start()

            val buffer = ByteBuffer.allocateDirect(1024 * 1024)
            val info = MediaCodec.BufferInfo()

            // Copy all video frames
            while (true) {
                buffer.clear()
                val size = videoExtractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = mapExtractorFlagsToCodecFlags(videoExtractor.sampleFlags)
                muxer.writeSampleData(muxerVideoTrack, buffer, info)
                videoExtractor.advance()
            }

            // Copy audio frames trimmed to video duration
            if (hasAudio && muxerAudioTrack >= 0) {
                while (true) {
                    buffer.clear()
                    val size = audioExtractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val ptsUs = audioExtractor.sampleTime
                    if (ptsUs > targetDurationUs) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = ptsUs
                    info.flags = mapExtractorFlagsToCodecFlags(audioExtractor.sampleFlags)
                    muxer.writeSampleData(muxerAudioTrack, buffer, info)
                    audioExtractor.advance()
                }
            }
        } finally {
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    suspend fun extractThumbnail(mp4File: File, outPng: File, durationSec: Double) = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(mp4File.absolutePath)
            val timeUs = (minOf(0.8, durationSec * 0.25) * 1_000_000.0).toLong().coerceAtLeast(0L)
            val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
            if (raw != null) {
                val scaled = Bitmap.createScaledBitmap(raw, 640, 360, true)
                FileOutputStream(outPng).use { fos ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 90, fos)
                }
                if (scaled !== raw) scaled.recycle()
                raw.recycle()
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun findTrackIndex(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    private fun mapExtractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var flags = 0
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }
}
