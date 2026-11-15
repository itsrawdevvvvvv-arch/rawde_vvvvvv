package com.rawedit.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

enum class TimelineLayerType(val id: String, val label: String) {
    TEXT("text", "Text"),
    IMAGE("image", "Image"),
    VIDEO("video", "Video"),
    SOLID("solid", "Solid"),
    SHAPE("shape", "Shape");

    companion object {
        fun fromId(id: String): TimelineLayerType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TEXT
    }
}

data class LayerKeyframe(
    val timeSec: Double,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float
)

data class SampledTransform(
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float
)

data class TimelineLayer(
    val id: String = "lyr_${System.currentTimeMillis()}_${(100..999).random()}",
    val type: TimelineLayerType,
    val name: String,
    val startSec: Double = 0.0,
    val endSec: Double = 5.0,
    val visible: Boolean = true,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 100f,
    val rotation: Float = 0f,
    val opacity: Float = 100f,
    val text: String = "It's Rawdev",
    val fontSize: Float = 72f,
    val colorHex: String = "#F3EFE6",
    val bgColorHex: String = "#C89F72",
    val assetRelativePath: String = "",
    val assetThumbPath: String = "",
    val shapeKind: String = "RECT", // RECT, CIRCLE, PILL
    val keyframes: List<LayerKeyframe> = emptyList()
) {
    fun sampleAt(tSec: Double): SampledTransform {
        if (keyframes.isEmpty()) {
            return SampledTransform(x, y, scale, rotation, opacity)
        }
        val sorted = keyframes.sortedBy { it.timeSec }
        if (tSec <= sorted.first().timeSec) {
            val k = sorted.first()
            return SampledTransform(k.x, k.y, k.scale, k.rotation, k.opacity)
        }
        if (tSec >= sorted.last().timeSec) {
            val k = sorted.last()
            return SampledTransform(k.x, k.y, k.scale, k.rotation, k.opacity)
        }
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (tSec in a.timeSec..b.timeSec) {
                val span = (b.timeSec - a.timeSec).coerceAtLeast(0.0001)
                val f = ((tSec - a.timeSec) / span).toFloat().coerceIn(0f, 1f)
                return SampledTransform(
                    x = a.x + (b.x - a.x) * f,
                    y = a.y + (b.y - a.y) * f,
                    scale = a.scale + (b.scale - a.scale) * f,
                    rotation = a.rotation + (b.rotation - a.rotation) * f,
                    opacity = a.opacity + (b.opacity - a.opacity) * f
                )
            }
        }
        return SampledTransform(x, y, scale, rotation, opacity)
    }
}

object LayerTimelineCodec {
    fun encode(layers: List<TimelineLayer>): String {
        val arr = JSONArray()
        layers.forEach { l ->
            arr.put(JSONObject().apply {
                put("id", l.id)
                put("type", l.type.id)
                put("name", l.name)
                put("startSec", l.startSec)
                put("endSec", l.endSec)
                put("visible", l.visible)
                put("x", l.x.toDouble())
                put("y", l.y.toDouble())
                put("scale", l.scale.toDouble())
                put("rotation", l.rotation.toDouble())
                put("opacity", l.opacity.toDouble())
                put("text", l.text)
                put("fontSize", l.fontSize.toDouble())
                put("colorHex", l.colorHex)
                put("bgColorHex", l.bgColorHex)
                put("assetRelativePath", l.assetRelativePath)
                put("assetThumbPath", l.assetThumbPath)
                put("shapeKind", l.shapeKind)
                put("keyframes", JSONArray().apply {
                    l.keyframes.sortedBy { it.timeSec }.forEach { k ->
                        put(JSONObject().apply {
                            put("timeSec", k.timeSec)
                            put("x", k.x.toDouble())
                            put("y", k.y.toDouble())
                            put("scale", k.scale.toDouble())
                            put("rotation", k.rotation.toDouble())
                            put("opacity", k.opacity.toDouble())
                        })
                    }
                })
            })
        }
        return arr.toString()
    }

    fun decode(json: String): List<TimelineLayer> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val kfArr = o.optJSONArray("keyframes") ?: JSONArray()
                val kfs = (0 until kfArr.length()).mapNotNull { ki ->
                    val ko = kfArr.optJSONObject(ki) ?: return@mapNotNull null
                    LayerKeyframe(
                        timeSec = ko.optDouble("timeSec", 0.0),
                        x = ko.optDouble("x", 0.0).toFloat(),
                        y = ko.optDouble("y", 0.0).toFloat(),
                        scale = ko.optDouble("scale", 100.0).toFloat(),
                        rotation = ko.optDouble("rotation", 0.0).toFloat(),
                        opacity = ko.optDouble("opacity", 100.0).toFloat()
                    )
                }
                TimelineLayer(
                    id = o.optString("id", "lyr_$i"),
                    type = TimelineLayerType.fromId(o.optString("type", "text")),
                    name = o.optString("name", "Layer ${i + 1}"),
                    startSec = o.optDouble("startSec", 0.0),
                    endSec = o.optDouble("endSec", 5.0),
                    visible = o.optBoolean("visible", true),
                    x = o.optDouble("x", 0.0).toFloat(),
                    y = o.optDouble("y", 0.0).toFloat(),
                    scale = o.optDouble("scale", 100.0).toFloat(),
                    rotation = o.optDouble("rotation", 0.0).toFloat(),
                    opacity = o.optDouble("opacity", 100.0).toFloat(),
                    text = o.optString("text", ""),
                    fontSize = o.optDouble("fontSize", 72.0).toFloat(),
                    colorHex = o.optString("colorHex", "#F3EFE6"),
                    bgColorHex = o.optString("bgColorHex", "#C89F72"),
                    assetRelativePath = o.optString("assetRelativePath", ""),
                    assetThumbPath = o.optString("assetThumbPath", ""),
                    shapeKind = o.optString("shapeKind", "RECT"),
                    keyframes = kfs
                )
            }
        }.getOrDefault(emptyList())
    }
}

object LayerTimelineCompiler {
    fun compileToHtml(
        baseHtml: String,
        layersJson: String,
        durationSec: Double
    ): String {
        val layers = LayerTimelineCodec.decode(layersJson).filter { it.visible }
        if (layers.isEmpty() && baseHtml.isNotBlank()) return baseHtml

        val safeDur = String.format(Locale.US, "%.3f", durationSec.coerceAtLeast(1.0))
        val safeJson = JSONArray(LayerTimelineCodec.encode(layers)).toString()
            .replace("</", "<\\/")

        val overlayScriptAndDom = """
        <style id="raw-layer-timeline-css">
          #raw-layer-overlay {
            position: fixed;
            inset: 0;
            width: 1920px;
            height: 1080px;
            pointer-events: none;
            overflow: hidden;
            z-index: 9999;
          }
          .raw-tl-item {
            position: absolute;
            left: 50%;
            top: 50%;
            transform-origin: center center;
            will-change: transform, opacity;
          }
        </style>
        <div id="raw-layer-overlay"></div>
        <script id="raw-layer-timeline-js">
        (function() {
          var LAYERS = $safeJson;
          var overlay = document.getElementById('raw-layer-overlay');
          var domMap = {};

          function lerp(a, b, f) { return a + (b - a) * f; }
          function sampleLayer(l, t) {
            var kfs = l.keyframes || [];
            if (!kfs.length) return { x: l.x || 0, y: l.y || 0, scale: l.scale == null ? 100 : l.scale, rotation: l.rotation || 0, opacity: l.opacity == null ? 100 : l.opacity };
            if (t <= kfs[0].timeSec) return kfs[0];
            if (t >= kfs[kfs.length - 1].timeSec) return kfs[kfs.length - 1];
            for (var i = 0; i < kfs.length - 1; i++) {
              var a = kfs[i], b = kfs[i + 1];
              if (t >= a.timeSec && t <= b.timeSec) {
                var span = Math.max(0.0001, b.timeSec - a.timeSec);
                var f = Math.max(0, Math.min(1, (t - a.timeSec) / span));
                return {
                  x: lerp(a.x, b.x, f),
                  y: lerp(a.y, b.y, f),
                  scale: lerp(a.scale, b.scale, f),
                  rotation: lerp(a.rotation, b.rotation, f),
                  opacity: lerp(a.opacity, b.opacity, f)
                };
              }
            }
            return kfs[kfs.length - 1];
          }

          LAYERS.forEach(function(l) {
            var el;
            if (l.type === 'video') {
              el = document.createElement('video');
              el.src = l.assetRelativePath || '';
              el.muted = true;
              el.playsInline = true;
              el.preload = 'auto';
              el.style.width = '720px';
              el.style.height = '720px';
              el.style.objectFit = 'contain';
              el.style.borderRadius = '24px';
            } else if (l.type === 'image') {
              el = document.createElement('img');
              el.src = l.assetRelativePath || '';
              el.style.width = '680px';
              el.style.height = '680px';
              el.style.objectFit = 'contain';
              el.style.borderRadius = '24px';
            } else if (l.type === 'solid') {
              el = document.createElement('div');
              el.style.width = '1920px';
              el.style.height = '1080px';
              el.style.background = l.bgColorHex || '#161618';
            } else if (l.type === 'shape') {
              el = document.createElement('div');
              el.style.width = '420px';
              el.style.height = '420px';
              el.style.background = l.bgColorHex || '#C89F72';
              el.style.borderRadius = l.shapeKind === 'CIRCLE' ? '9999px' : (l.shapeKind === 'PILL' ? '999px' : '36px');
            } else {
              el = document.createElement('div');
              el.textContent = l.text || '';
              el.style.fontFamily = "'Montserrat', 'Poppins', sans-serif";
              el.style.fontWeight = '800';
              el.style.fontSize = (l.fontSize || 72) + 'px';
              el.style.color = l.colorHex || '#F3EFE6';
              el.style.whiteSpace = 'nowrap';
              el.style.textAlign = 'center';
            }
            el.className = 'raw-tl-item';
            el.style.display = 'none';
            overlay.appendChild(el);
            domMap[l.id] = el;
          });

          window.__updateLayerData = function(jsonStr) {
            try {
              var next = JSON.parse(jsonStr);
              if (Array.isArray(next)) LAYERS = next;
            } catch (_) {}
          };

          window.__applyLayerTimeline = function(t) {
            var promises = [];
            LAYERS.forEach(function(l) {
              var el = domMap[l.id];
              if (!el) return;
              if (t < l.startSec || t > l.endSec) {
                el.style.display = 'none';
                return;
              }
              el.style.display = 'block';
              var st = sampleLayer(l, t);
              var sc = (st.scale == null ? 100 : st.scale) / 100.0;
              var op = Math.max(0, Math.min(1, (st.opacity == null ? 100 : st.opacity) / 100.0));
              el.style.opacity = String(op);
              el.style.transform = 'translate(-50%, -50%) translate(' + (st.x || 0) + 'px, ' + (st.y || 0) + 'px) rotate(' + (st.rotation || 0) + 'deg) scale(' + sc + ')';
              if (l.type === 'video' && window.RAW && typeof window.RAW.syncVideo === 'function') {
                promises.push(window.RAW.syncVideo(el, t, l.startSec, l.endSec, 1));
              }
            });
            return Promise.all(promises);
          };

          if (window.RAW && typeof window.RAW.seek === 'function') {
            var origSeek = window.RAW.seek.bind(window.RAW);
            window.RAW.seek = function(t) {
              return Promise.all([Promise.resolve(origSeek(t)), window.__applyLayerTimeline(t)]);
            };
          } else {
            window.RAW = {
              duration: $safeDur,
              width: 1920,
              height: 1080,
              fps: 30,
              seek: function(t) { return window.__applyLayerTimeline(t); }
            };
          }
        })();
        </script>
        """.trimIndent()

        if (baseHtml.isBlank()) {
            return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8" />
              <style>
                html, body { margin: 0; padding: 0; width: 1920px; height: 1080px; overflow: hidden; background: #0D0D0F; }
                #stage { position: relative; width: 1920px; height: 1080px; overflow: hidden; }
              </style>
            </head>
            <body>
              <div id="stage"></div>
              $overlayScriptAndDom
            </body>
            </html>
            """.trimIndent()
        }

        val lower = baseHtml.lowercase(Locale.US)
        val bodyClose = lower.lastIndexOf("</body>")
        return if (bodyClose >= 0) {
            baseHtml.substring(0, bodyClose) + "\n$overlayScriptAndDom\n" + baseHtml.substring(bodyClose)
        } else {
            baseHtml + "\n$overlayScriptAndDom"
        }
    }
}
