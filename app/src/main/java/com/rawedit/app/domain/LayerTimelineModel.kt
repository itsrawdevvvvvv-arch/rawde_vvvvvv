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
    val domEid: String = "",
    val textEdited: Boolean = false,
    val colorEdited: Boolean = false,
    val bgColorEdited: Boolean = false,
    val fontSizeEdited: Boolean = false,
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
                put("domEid", l.domEid)
                put("textEdited", l.textEdited)
                put("colorEdited", l.colorEdited)
                put("bgColorEdited", l.bgColorEdited)
                put("fontSizeEdited", l.fontSizeEdited)
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
                    domEid = o.optString("domEid", ""),
                    textEdited = o.optBoolean("textEdited", false),
                    colorEdited = o.optBoolean("colorEdited", false),
                    bgColorEdited = o.optBoolean("bgColorEdited", false),
                    fontSizeEdited = o.optBoolean("fontSizeEdited", false),
                    keyframes = kfs
                )
            }
        }.getOrDefault(emptyList())
    }
}

object LayerTimelineCompiler {

    /**
     * JS executed inside WebView to tag & extract every editable HTML element
     * (text, cards/shapes, images, videos) arranged by its scene timing.
     */
    const val EXTRACT_DOM_ELEMENTS_JS = """
    (function() {
      var stage = document.getElementById('stage') || document.body;
      if (!stage) return '[]';
      var proj = window.PROJECT || {};
      var totalDur = Number(proj.duration) || (window.RAW && Number(window.RAW.duration)) || 15;
      var scenes = Array.isArray(proj.scenes) ? proj.scenes : [];
      var sceneEls = stage.querySelectorAll('.scene');
      var out = [];
      var idx = 0;

      function rgbToHex(rgb) {
        if (!rgb || rgb === 'transparent' || rgb === 'rgba(0, 0, 0, 0)') return '';
        var m = rgb.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
        if (!m) return '';
        function hex(n) { return ('0' + parseInt(n, 10).toString(16)).slice(-2); }
        return ('#' + hex(m[1]) + hex(m[2]) + hex(m[3])).toUpperCase();
      }

      function addEl(el, sStart, sEnd) {
        if (!el || el.nodeType !== 1) return;
        if (el.id === 'raw-layer-overlay') return;
        var tag = (el.tagName || '').toUpperCase();
        if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'LINK') return;
        var cls = (el.className && typeof el.className === 'string') ? el.className.toLowerCase() : '';
        if (cls.indexOf('dust') !== -1 || cls.indexOf('grain') !== -1) return;

        var eid = el.getAttribute('data-raw-eid');
        if (!eid) {
          eid = 'dom_' + idx;
          el.setAttribute('data-raw-eid', eid);
        }
        idx++;

        var cs = getComputedStyle(el);
        var txt = (el.innerText || el.textContent || '').trim().replace(/\s+/g, ' ');
        var type = 'shape';
        if (tag === 'VIDEO') type = 'video';
        else if (tag === 'IMG') type = 'image';
        else if (tag === 'SVG' || cls.indexOf('card') !== -1 || cls.indexOf('phone') !== -1 || cls.indexOf('progress') !== -1 || cls.indexOf('box') !== -1 || cls.indexOf('panel') !== -1) type = 'shape';
        else if (txt.length > 0 && el.children.length <= 2) type = 'text';

        var label = '';
        if (type === 'text') label = txt.slice(0, 26);
        else if (type === 'video') label = 'Video: ' + (el.getAttribute('src') || 'clip').split('/').pop();
        else if (type === 'image') label = 'Image: ' + (el.getAttribute('src') || 'img').split('/').pop();
        else label = (txt ? txt.slice(0, 20) : (cls.split(' ')[0] || tag.toLowerCase()));

        out.push({
          eid: eid,
          type: type,
          name: label || ('Element ' + idx),
          startSec: sStart,
          endSec: sEnd,
          text: txt.slice(0, 200),
          fontSize: parseFloat(cs.fontSize) || 48,
          colorHex: rgbToHex(cs.color) || '#1A1A1E',
          bgColorHex: rgbToHex(cs.backgroundColor) || '#C89F72'
        });
      }

      if (sceneEls.length > 0) {
        for (var si = 0; si < sceneEls.length; si++) {
          var scEl = sceneEls[si];
          var scMeta = scenes[si] || {};
          var st = Number(scMeta.start);
          var en = Number(scMeta.end);
          if (!Number.isFinite(st)) st = (si * totalDur) / sceneEls.length;
          if (!Number.isFinite(en) || en <= st) en = ((si + 1) * totalDur) / sceneEls.length;
          var kids = scEl.children;
          for (var ki = 0; ki < kids.length; ki++) {
            addEl(kids[ki], st, en);
          }
        }
      } else {
        var topKids = stage.children;
        for (var ti = 0; ti < topKids.length; ti++) {
          addEl(topKids[ti], 0, totalDur);
        }
      }
      return JSON.stringify(out.slice(0, 48));
    })();
    """

    fun compileToHtml(
        baseHtml: String,
        layersJson: String,
        durationSec: Double
    ): String {
        val layers = LayerTimelineCodec.decode(layersJson)
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

          function tagDomElements() {
            var stage = document.getElementById('stage') || document.body;
            if (!stage) return;
            var sceneEls = stage.querySelectorAll('.scene');
            var idx = 0;
            function tagEl(el) {
              if (!el || el.nodeType !== 1 || el.id === 'raw-layer-overlay') return;
              var tag = (el.tagName || '').toUpperCase();
              if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'LINK') return;
              var cls = (el.className && typeof el.className === 'string') ? el.className.toLowerCase() : '';
              if (cls.indexOf('dust') !== -1 || cls.indexOf('grain') !== -1) return;
              if (!el.getAttribute('data-raw-eid')) el.setAttribute('data-raw-eid', 'dom_' + idx);
              idx++;
            }
            if (sceneEls.length > 0) {
              for (var si = 0; si < sceneEls.length; si++) {
                var kids = sceneEls[si].children;
                for (var ki = 0; ki < kids.length; ki++) tagEl(kids[ki]);
              }
            } else {
              var topKids = stage.children;
              for (var ti = 0; ti < topKids.length; ti++) tagEl(topKids[ti]);
            }
          }
          tagDomElements();

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

          function buildCustomOverlays() {
            overlay.innerHTML = '';
            domMap = {};
            LAYERS.forEach(function(l) {
              if (l.domEid) return;
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
          }
          buildCustomOverlays();

          window.__updateLayerData = function(jsonStr) {
            try {
              var next = JSON.parse(jsonStr);
              if (Array.isArray(next)) {
                var prevCustomCount = LAYERS.filter(function(x){ return !x.domEid; }).length;
                var nextCustomCount = next.filter(function(x){ return !x.domEid; }).length;
                LAYERS = next;
                if (prevCustomCount !== nextCustomCount) buildCustomOverlays();
              }
            } catch (_) {}
          };

          window.__applyLayerTimeline = function(t) {
            tagDomElements();
            var promises = [];
            LAYERS.forEach(function(l) {
              var st = sampleLayer(l, t);
              var sc = (st.scale == null ? 100 : st.scale) / 100.0;
              var op = Math.max(0, Math.min(1, (st.opacity == null ? 100 : st.opacity) / 100.0));

              if (l.domEid) {
                var domEl = document.querySelector('[data-raw-eid="' + l.domEid + '"]');
                if (!domEl) return;
                if (!l.visible || t < l.startSec || t > l.endSec) {
                  domEl.style.visibility = 'hidden';
                  return;
                }
                domEl.style.visibility = 'visible';
                domEl.style.translate = (st.x || 0) + 'px ' + (st.y || 0) + 'px';
                domEl.style.rotate = (st.rotation || 0) + 'deg';
                domEl.style.scale = String(sc);
                if (op < 0.999) domEl.style.opacity = String(op);
                if (l.textEdited && l.type === 'text') domEl.textContent = l.text || '';
                if (l.colorEdited && l.colorHex) domEl.style.color = l.colorHex;
                if (l.bgColorEdited && l.bgColorHex) domEl.style.backgroundColor = l.bgColorHex;
                if (l.fontSizeEdited && l.fontSize) domEl.style.fontSize = l.fontSize + 'px';
                return;
              }

              var el = domMap[l.id];
              if (!el) return;
              if (!l.visible || t < l.startSec || t > l.endSec) {
                el.style.display = 'none';
                return;
              }
              el.style.display = 'block';
              el.style.opacity = String(op);
              el.style.transform = 'translate(-50%, -50%) translate(' + (st.x || 0) + 'px, ' + (st.y || 0) + 'px) rotate(' + (st.rotation || 0) + 'deg) scale(' + sc + ')';
              if (l.type === 'text') {
                el.textContent = l.text || '';
                el.style.color = l.colorHex || '#F3EFE6';
                el.style.fontSize = (l.fontSize || 72) + 'px';
              } else if (l.type === 'solid' || l.type === 'shape') {
                el.style.background = l.bgColorHex || '#C89F72';
              }
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
