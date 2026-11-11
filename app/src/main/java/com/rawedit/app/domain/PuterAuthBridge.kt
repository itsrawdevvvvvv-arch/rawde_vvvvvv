package com.rawedit.app.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLEncoder

data class PuterLoopbackSession(
    val port: Int,
    val redirectUrl: String,
    val authUrl: String,
    internal val serverSocket: ServerSocket
) {
    fun closeQuietly() {
        runCatching { serverSocket.close() }
    }
}

object PuterAuthBridge {

    fun openUrlInChromeOrBrowser(context: Context, url: String) {
        val uri = Uri.parse(url)
        val chromePkg = "com.android.chrome"
        if (isPackageInstalled(context, chromePkg)) {
            val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(chromePkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching { context.startActivity(chromeIntent) }.isSuccess
            if (started) return
        }
        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallbackIntent) }
    }

    fun openPuterSiteForAccountLogout(context: Context) {
        openUrlInChromeOrBrowser(context, PuterCodec.WEB_ORIGIN)
    }

    fun startLoopbackSession(): PuterLoopbackSession {
        val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply {
            soTimeout = 180_000
        }
        val port = socket.localPort
        val redirectUrl = "http://127.0.0.1:$port/callback"
        val authUrl = PuterCodec.buildAuthMeUrl(redirectUrl)
        return PuterLoopbackSession(
            port = port,
            redirectUrl = redirectUrl,
            authUrl = authUrl,
            serverSocket = socket
        )
    }

    suspend fun awaitCallbackToken(session: PuterLoopbackSession): String? = withContext(Dispatchers.IO) {
        try {
            val serverSocket = session.serverSocket
            val deadlineMs = System.currentTimeMillis() + 180_000L
            while (System.currentTimeMillis() < deadlineMs && !serverSocket.isClosed) {
                val client = try {
                    serverSocket.accept()
                } catch (_: Throwable) {
                    return@withContext null
                }

                try {
                    client.soTimeout = 5_000
                    val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                    val requestLine = reader.readLine().orEmpty()
                    val pathPart = requestLine.split(' ').getOrNull(1).orEmpty()
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                    }

                    val token = PuterCodec.extractTokenFromCallbackUri("http://127.0.0.1:${session.port}$pathPart")
                    val os = client.getOutputStream()
                    if (pathPart.startsWith("/favicon.ico")) {
                        val notFound = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                        os.write(notFound.toByteArray(Charsets.US_ASCII))
                        os.flush()
                    } else if (!token.isNullOrBlank()) {
                        val deepLink = "${PuterCodec.APP_REDIRECT_URI}?token=${URLEncoder.encode(token, "UTF-8")}"
                        val html = buildSuccessHtmlPage(deepLink)
                        val bodyBytes = html.toByteArray(Charsets.UTF_8)
                        val headers = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: text/html; charset=utf-8\r\n")
                            append("Cache-Control: no-store\r\n")
                            append("Connection: close\r\n")
                            append("Content-Length: ${bodyBytes.size}\r\n\r\n")
                        }
                        os.write(headers.toByteArray(Charsets.US_ASCII))
                        os.write(bodyBytes)
                        os.flush()
                        return@withContext token
                    } else {
                        val fallbackBytes = buildWaitingFragmentPage().toByteArray(Charsets.UTF_8)
                        val headers = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: text/html; charset=utf-8\r\n")
                            append("Cache-Control: no-store\r\n")
                            append("Connection: close\r\n")
                            append("Content-Length: ${fallbackBytes.size}\r\n\r\n")
                        }
                        os.write(headers.toByteArray(Charsets.US_ASCII))
                        os.write(fallbackBytes)
                        os.flush()
                    }
                } finally {
                    runCatching { client.close() }
                }
            }
            null
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        } finally {
            session.closeQuietly()
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildWaitingFragmentPage(): String {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <title>Completing Puter sign-in...</title>
            </head>
            <body style="background:#0D0D0F;color:#F7ECE6;font-family:sans-serif;padding:24px;text-align:center;">
              <p>Finishing Puter authentication...</p>
              <script>
                (function() {
                  var h = (window.location.hash || '').replace(/^#/, '');
                  var p = new URLSearchParams(h);
                  var tok = p.get('token') || p.get('puter.auth_token') || p.get('auth_token');
                  if (tok) {
                    window.location.replace('/callback?token=' + encodeURIComponent(tok));
                  }
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSuccessHtmlPage(deepLinkUrl: String): String {
        val safeHref = deepLinkUrl
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <meta http-equiv="refresh" content="0;url=$safeHref" />
              <title>Signed in to raw_edit</title>
              <style>
                body {
                  margin: 0;
                  min-height: 100vh;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  background: #0D0D0F;
                  color: #F7ECE6;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  padding: 24px;
                }
                .card {
                  max-width: 360px;
                  width: 100%;
                  background: #17171A;
                  border-radius: 28px;
                  padding: 28px 24px;
                  text-align: center;
                }
                h1 {
                  margin: 0 0 8px;
                  font-size: 22px;
                }
                p {
                  margin: 0 0 20px;
                  color: #A99D95;
                  font-size: 14px;
                  line-height: 1.5;
                }
                a.btn {
                  display: inline-block;
                  background: #F7ECE6;
                  color: #0D0D0F;
                  text-decoration: none;
                  font-weight: 700;
                  padding: 13px 22px;
                  border-radius: 999px;
                  font-size: 15px;
                }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>Signed in with Puter</h1>
                <p>Returning to raw_edit automatically. Tap below if Chrome stays open.</p>
                <a class="btn" href="$safeHref">Return to raw_edit</a>
              </div>
              <script>
                setTimeout(function() { window.location.href = "$safeHref"; }, 120);
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
