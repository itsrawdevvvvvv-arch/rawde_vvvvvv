package com.rawedit.app.domain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

data class IconSearchItem(
    val fullId: String,
    val prefix: String,
    val name: String,
    val collectionTitle: String,
    val licenseTitle: String,
    val rawSvg: String,
    val supportsTint: Boolean
)

sealed interface DownloadedWebMedia {
    val suggestedName: String

    data class Svg(
        val rawSvg: String,
        override val suggestedName: String
    ) : DownloadedWebMedia

    data class Raster(
        val bitmap: Bitmap,
        val isPngLike: Boolean,
        override val suggestedName: String
    ) : DownloadedWebMedia
}

object WebAssetFetcher {

    private const val MAX_REMOTE_BYTES = 12 * 1024 * 1024
    private const val MAX_SVG_BYTES = 512 * 1024
    private const val USER_AGENT = "raw_edit-Android/1.0"

    suspend fun searchIcons(
        query: String,
        limit: Int = 24
    ): Result<List<IconSearchItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQuery = query.trim()
            if (cleanQuery.isEmpty()) throw IllegalArgumentException("Enter an icon keyword.")
            val qEnc = URLEncoder.encode(cleanQuery, "UTF-8")
            val searchUrl = "https://api.iconify.design/search?query=$qEnc&limit=${limit.coerceIn(4, 36)}"
            val jsonBody = httpGetText(searchUrl, MAX_SVG_BYTES)
            val root = JSONObject(jsonBody)
            val iconsArr = root.optJSONArray("icons") ?: return@runCatching emptyList()
            val collectionsObj = root.optJSONObject("collections")

            val ids = ArrayList<String>(iconsArr.length())
            for (i in 0 until iconsArr.length()) {
                val id = iconsArr.optString(i, "").trim()
                if (id.contains(':')) ids.add(id)
            }
            if (ids.isEmpty()) return@runCatching emptyList()

            val sem = Semaphore(6)
            coroutineScope {
                ids.map { fullId ->
                    async {
                        sem.withPermit {
                            val prefix = fullId.substringBefore(':')
                            val name = fullId.substringAfter(':')
                            val colMeta = collectionsObj?.optJSONObject(prefix)
                            val colTitle = colMeta?.optString("name", prefix)?.takeIf { it.isNotBlank() } ?: prefix
                            val licTitle = colMeta?.optJSONObject("license")?.optString("title", "")
                                ?.takeIf { it.isNotBlank() } ?: "Open"

                            val svgUrl = "https://api.iconify.design/$prefix/$name.svg"
                            val rawSvg = runCatching { httpGetText(svgUrl, MAX_SVG_BYTES) }.getOrNull().orEmpty()
                            val cleanSvg = SvgSanitizerAndPadding.sanitizeSvg(rawSvg)
                            if (cleanSvg.isBlank()) {
                                null
                            } else {
                                IconSearchItem(
                                    fullId = fullId,
                                    prefix = prefix,
                                    name = name,
                                    collectionTitle = colTitle,
                                    licenseTitle = licTitle,
                                    rawSvg = cleanSvg,
                                    supportsTint = cleanSvg.contains("currentColor", ignoreCase = true)
                                )
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
    }

    suspend fun downloadMedia(
        urlOrDataUri: String,
        suggestedName: String = "web_asset"
    ): Result<DownloadedWebMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = urlOrDataUri.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("Empty asset URL.")

            if (trimmed.startsWith("data:", ignoreCase = true)) {
                return@runCatching decodeDataUri(trimmed, suggestedName)
            }

            val lower = trimmed.lowercase(Locale.US)
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                throw IllegalArgumentException("Unsupported URL scheme.")
            }

            val (bytes, contentType) = httpGetBytes(trimmed, MAX_REMOTE_BYTES)
            val fallbackName = deriveNameFromUrl(trimmed, suggestedName)
            val ctLower = contentType.lowercase(Locale.US)
            val headText = bytes.copyOfRange(0, minOf(bytes.size, 512))
                .toString(Charsets.UTF_8)
                .trimStart()

            if (ctLower.contains("svg") ||
                lower.substringBefore('?').endsWith(".svg") ||
                headText.contains("<svg", ignoreCase = true)
            ) {
                val svgText = SvgSanitizerAndPadding.sanitizeSvg(bytes.toString(Charsets.UTF_8))
                if (svgText.isBlank()) {
                    throw IllegalArgumentException("Invalid or empty SVG resource.")
                }
                return@runCatching DownloadedWebMedia.Svg(
                    rawSvg = svgText,
                    suggestedName = fallbackName
                )
            }

            val isPngLike = ctLower.contains("png") || ctLower.contains("webp") ||
                lower.substringBefore('?').let { it.endsWith(".png") || it.endsWith(".webp") }
            val bmp = decodeScaledBitmap(bytes, maxDim = 1440)
                ?: throw IllegalArgumentException("Could not decode image bytes.")
            DownloadedWebMedia.Raster(
                bitmap = bmp,
                isPngLike = isPngLike || bmp.hasAlpha(),
                suggestedName = fallbackName
            )
        }
    }

    private fun decodeDataUri(uri: String, suggestedName: String): DownloadedWebMedia {
        val commaIdx = uri.indexOf(',')
        if (commaIdx < 5) throw IllegalArgumentException("Malformed data URI.")
        val header = uri.substring(5, commaIdx).lowercase(Locale.US)
        val payload = uri.substring(commaIdx + 1)
        val isBase64 = header.contains(";base64")

        val bytes = if (isBase64) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
        }

        if (header.contains("svg")) {
            val cleanSvg = SvgSanitizerAndPadding.sanitizeSvg(bytes.toString(Charsets.UTF_8))
            if (cleanSvg.isBlank()) throw IllegalArgumentException("Empty inline SVG.")
            return DownloadedWebMedia.Svg(rawSvg = cleanSvg, suggestedName = suggestedName)
        }

        val bmp = decodeScaledBitmap(bytes, maxDim = 1440)
            ?: throw IllegalArgumentException("Could not decode data URI image.")
        val isPngLike = header.contains("png") || header.contains("webp") || bmp.hasAlpha()
        return DownloadedWebMedia.Raster(
            bitmap = bmp,
            isPngLike = isPngLike,
            suggestedName = suggestedName
        )
    }

    private fun decodeScaledBitmap(bytes: ByteArray, maxDim: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return null

        val maxSide = maxOf(decoded.width, decoded.height)
        if (maxSide <= maxDim) {
            return if (decoded.config == Bitmap.Config.ARGB_8888) {
                decoded
            } else {
                decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
            }
        }
        val scale = maxDim.toFloat() / maxSide.toFloat()
        val w = (decoded.width * scale).toInt().coerceAtLeast(1)
        val h = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, w, h, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun deriveNameFromUrl(url: String, fallback: String): String {
        val rawSeg = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val base = rawSeg.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .take(28)
        return base.ifBlank { fallback }
    }

    private fun httpGetText(urlStr: String, maxBytes: Int): String {
        val (bytes, _) = httpGetBytes(urlStr, maxBytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun httpGetBytes(urlStr: String, maxBytes: Int): Pair<ByteArray, String> {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 5) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            try {
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val loc = conn.getHeaderField("Location")?.trim()
                        ?: throw IllegalStateException("Redirect missing Location")
                    currentUrl = URL(url, loc).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code from host")
                }
                val contentType = conn.contentType.orEmpty()
                val contentLength = conn.contentLengthLong
                if (contentLength > maxBytes) {
                    throw IllegalArgumentException("File exceeds ${maxBytes / (1024 * 1024)} MB limit.")
                }
                val baos = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                conn.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > maxBytes) {
                            throw IllegalArgumentException("File exceeds ${maxBytes / (1024 * 1024)} MB limit.")
                        }
                        baos.write(buf, 0, n)
                    }
                }
                return baos.toByteArray() to contentType
            } finally {
                conn.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects")
    }
}
