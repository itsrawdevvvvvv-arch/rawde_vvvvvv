package com.rawedit.app.domain

import android.content.Context
import java.io.File

/**
 * Tiny file-based version history for a project's HTML so any AI edit can be undone.
 * Stored under filesDir/html_versions/<projectId>/<timestamp>.html (+ .label sidecar).
 */
data class HtmlVersion(val file: File, val label: String, val atMs: Long)

object HtmlVersionStore {
    private const val MAX_VERSIONS = 15

    private fun dir(context: Context, projectId: Long): File =
        File(context.filesDir, "html_versions/$projectId").also { it.mkdirs() }

    fun save(context: Context, projectId: Long, html: String, label: String) {
        if (html.isBlank()) return
        runCatching {
            val d = dir(context, projectId)
            val latest = list(context, projectId).firstOrNull()
            if (latest != null && latest.file.readText() == html) return
            val ts = System.currentTimeMillis()
            File(d, "$ts.html").writeText(html)
            File(d, "$ts.label").writeText(label.take(80))
            list(context, projectId).drop(MAX_VERSIONS).forEach { v ->
                v.file.delete()
                File(v.file.parentFile, v.file.nameWithoutExtension + ".label").delete()
            }
        }
    }

    /** Newest first. */
    fun list(context: Context, projectId: Long): List<HtmlVersion> {
        val d = dir(context, projectId)
        return (d.listFiles { f -> f.extension == "html" } ?: emptyArray())
            .mapNotNull { f ->
                val ts = f.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                val label = runCatching { File(d, "$ts.label").readText() }.getOrDefault("")
                HtmlVersion(f, label, ts)
            }
            .sortedByDescending { it.atMs }
    }

    fun readHtml(version: HtmlVersion): String = runCatching { version.file.readText() }.getOrDefault("")

    fun remove(version: HtmlVersion) {
        version.file.delete()
        File(version.file.parentFile, version.file.nameWithoutExtension + ".label").delete()
    }
}
