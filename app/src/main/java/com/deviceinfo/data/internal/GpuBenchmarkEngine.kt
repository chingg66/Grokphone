package com.deviceinfo.data.internal

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 以真正的 OpenGL ES 渲染負載量測 GPU 吞吐量。
 *
 * 不使用 SurfaceView：測試只需要一張離屏 pbuffer，不會被螢幕刷新率或 UI 合成限制。
 * 每幀以 glFinish() 等待 GPU 完成，確保量到的是實際渲染時間，而不是排隊提交的時間。
 */
internal class GpuBenchmarkEngine {

    data class Progress(
        val elapsedMs: Long,
        val avgFps: Float,
        val currentFps: Float,
        val frameCount: Long
    )

    data class Stats(
        val frameCount: Long,
        val avgFps: Float,
        val peakFps: Float,
        val minFps: Float,
        val renderer: String
    )

    fun run(durationMs: Long, onProgress: (Progress) -> Boolean): Stats {
        require(durationMs > 0)
        val egl = EglCore()
        var program = 0
        var vertexShader = 0
        var fragmentShader = 0
        try {
            egl.init()
            egl.makeCurrent()
            vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            program = linkProgram(vertexShader, fragmentShader)
            GLES20.glUseProgram(program)

            val positionLocation = GLES20.glGetAttribLocation(program, "a_Position")
            check(positionLocation >= 0) { "GPU 頂點著色器缺少 a_Position" }
            val timeLocation = GLES20.glGetUniformLocation(program, "u_Time")
            val vertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
            val vertexBuffer: FloatBuffer = ByteBuffer
                .allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(vertices); position(0) }
            GLES20.glEnableVertexAttribArray(positionLocation)
            GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glViewport(0, 0, RENDER_SIZE, RENDER_SIZE)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)

            // 讓驅動先完成編譯與快取，避免把初始化成本算進第一個取樣。
            val warmupStart = System.nanoTime()
            var warmupFrame = 0
            while ((System.nanoTime() - warmupStart) / 1_000_000L < WARMUP_MS) {
                drawFrame(timeLocation, warmupFrame * 0.016f)
                warmupFrame++
            }

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
                ?.takeIf { it.isNotBlank() } ?: "OpenGL ES"

            var frameCount = 0L
            var reportFrames = 0L
            var secondFrames = 0L
            var peakFps = 0f
            var minFps = Float.MAX_VALUE
            val startNs = System.nanoTime()
            var lastReportNs = startNs
            var secondStartNs = startNs

            while (true) {
                drawFrame(timeLocation, frameCount * 0.016f)
                frameCount++
                secondFrames++
                val now = System.nanoTime()
                val elapsedMs = (now - startNs) / 1_000_000L

                if (now - secondStartNs >= 1_000_000_000L) {
                    val secondFps = secondFrames * 1_000_000_000f / (now - secondStartNs)
                    if (secondFps > 0f) {
                        peakFps = max(peakFps, secondFps)
                        minFps = min(minFps, secondFps)
                    }
                    secondFrames = 0L
                    secondStartNs = now
                }

                if (elapsedMs >= durationMs || now - lastReportNs >= REPORT_INTERVAL_NS) {
                    val avgFps = frameCount * 1_000_000_000f / (now - startNs)
                    val currentFps = (frameCount - reportFrames) * 1_000_000_000f / (now - lastReportNs)
                    val keepGoing = onProgress(
                        Progress(
                            elapsedMs = elapsedMs.coerceAtMost(durationMs),
                            avgFps = avgFps,
                            currentFps = currentFps,
                            frameCount = frameCount
                        )
                    )
                    reportFrames = frameCount
                    lastReportNs = now
                    if (!keepGoing) break
                }
                if (elapsedMs >= durationMs) break
            }

            val elapsedNs = (System.nanoTime() - startNs).coerceAtLeast(1L)
            val avgFps = frameCount * 1_000_000_000f / elapsedNs
            if (minFps == Float.MAX_VALUE) minFps = avgFps
            if (peakFps <= 0f) peakFps = avgFps
            return Stats(
                frameCount = frameCount,
                avgFps = avgFps,
                peakFps = peakFps,
                minFps = minFps,
                renderer = renderer
            )
        } finally {
            if (program != 0) GLES20.glDeleteProgram(program)
            if (vertexShader != 0) GLES20.glDeleteShader(vertexShader)
            if (fragmentShader != 0) GLES20.glDeleteShader(fragmentShader)
            egl.release()
        }
    }

    private fun drawFrame(timeLocation: Int, timeSeconds: Float) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUniform1f(timeLocation, timeSeconds)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        // 確保這一幀真的完成，避免只量到指令提交速度。
        GLES20.glFinish()
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "無法建立 GPU 著色器" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("GPU 著色器編譯失敗：$log")
        }
        return shader
    }

    private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES20.glCreateProgram()
        check(program != 0) { "無法建立 GPU 測試程式" }
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("GPU 測試程式連結失敗：$log")
        }
        return program
    }

    companion object {
        const val SCORE_MULTIPLIER = 1000
        private const val RENDER_SIZE = 1280
        private const val WARMUP_MS = 900L
        private const val REPORT_INTERVAL_NS = 500_000_000L

        fun scoreFor(avgFps: Float): Int = (avgFps * SCORE_MULTIPLIER).roundToInt().coerceAtLeast(0)

        private const val VERTEX_SHADER = """
            attribute vec2 a_Position;
            varying vec2 vUv;
            void main() {
                vUv = a_Position * 0.5 + 0.5;
                gl_Position = vec4(a_Position, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vUv;
            uniform float u_Time;
            void main() {
                vec2 uv = vUv * 2.0 - 1.0;
                vec3 color = vec3(0.0);
                for (int i = 0; i < 24; i++) {
                    float fi = float(i);
                    float angle = fi * 0.71 + u_Time * (0.16 + fi * 0.0025);
                    vec2 center = vec2(cos(angle), sin(angle * 1.13)) * 0.58;
                    vec2 p = uv;
                    p.x += sin(u_Time * 0.7 + fi) * 0.12;
                    p.y += cos(u_Time * 0.5 + fi * 1.3) * 0.12;
                    float d = length(p - center);
                    color += vec3(0.05, 0.12, 0.08) / (d * d + 0.015);
                }
                color = 1.0 - exp(-color * 0.32);
                color = pow(color, vec3(0.85));
                gl_FragColor = vec4(color, 1.0);
            }
        """
    }

    private class EglCore {
        private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var context: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

        fun init() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "裝置沒有可用的 EGL 顯示" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL 初始化失敗" }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            check(
                EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, configCount, 0) &&
                    configCount[0] > 0
            ) { "找不到支援 OpenGL ES 2.0 的 GPU 設定" }
            val config = configs[0] ?: error("GPU EGL 設定為空")

            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "無法建立 GPU 測試上下文" }
            surface = EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(EGL14.EGL_WIDTH, RENDER_SIZE, EGL14.EGL_HEIGHT, RENDER_SIZE, EGL14.EGL_NONE),
                0
            )
            check(surface != EGL14.EGL_NO_SURFACE) { "無法建立 GPU 測試表面" }
        }

        fun makeCurrent() {
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "無法啟用 GPU 測試上下文" }
        }

        fun release() {
            if (display == EGL14.EGL_NO_DISPLAY) return
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            surface = EGL14.EGL_NO_SURFACE
            context = EGL14.EGL_NO_CONTEXT
            display = EGL14.EGL_NO_DISPLAY
        }
    }
}
