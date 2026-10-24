package com.deviceinfo.data.internal

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import com.deviceinfo.core.L10n
import com.deviceinfo.R
import com.deviceinfo.data.model.GpuInfo

/**
 * GPU 探測。
 *
 * 優先級：kgsl / kernel 節點 → 離屏 EGL 上下文取 GL_RENDERER → 未知。
 *
 * 两个必须遵守的细节：
 * 1. EGL_NO_DISPLAY / EGL_NO_CONTEXT 等常量在部分实现（包括模拟环境）里是 null，
 *    所以所有引用它们的局部变量必须可空，且初始化不能直接赋这些常量 —— 否则会 NPE。
 * 2. EGL 资源必须在 finally 里彻底销毁，否则反复进入页面会累积 GL 上下文。
 */
internal object GpuProbe {

    fun probe(): GpuInfo {
        nodeProbe()?.let { return it }
        eglProbe()?.let { return it }
        return GpuInfo(
            renderer = L10n.s(R.string.prb_gpu_unknown),
            vendor = "—",
            glVersion = "—",
            source = "fallback"
        )
    }

    private fun nodeProbe(): GpuInfo? {
        val model = SysFs.firstText(
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/kernel/gpu/gpu_model",
            "/proc/gpuinfo"
        ) ?: return null
        val firstLine = model.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) return null
        val clock = SysFs.firstText(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk"
        )
        return GpuInfo(
            renderer = firstLine,
            vendor = firstLine.substringBefore(' ').ifBlank { "—" },
            glVersion = if (clock != null) L10n.s(R.string.prb_gpu_node_readable) else "—",
            source = "sysfs"
        )
    }

    private fun eglProbe(): GpuInfo? {
        var display: EGLDisplay? = null
        var context: EGLContext? = null
        var surface: EGLSurface? = null

        return try {
            val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY) ?: return null
            display = eglDisplay

            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return null

            val configs = arrayOfNulls<EGLConfig>(1)
            val configCount = IntArray(1)
            val attributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            if (!EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, 1, configCount, 0)) {
                return null
            }
            if (configCount[0] <= 0) return null
            val config = configs[0] ?: return null

            val eglContext = EGL14.eglCreateContext(
                eglDisplay,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            ) ?: return null
            context = eglContext

            val eglSurface = EGL14.eglCreatePbufferSurface(
                eglDisplay,
                config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                0
            ) ?: return null
            surface = eglSurface

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return null

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
            if (renderer.isBlank()) return null

            GpuInfo(
                renderer = renderer,
                vendor = vendor.ifBlank { "—" },
                glVersion = glVersion.ifBlank { "—" },
                source = "egl"
            )
        } catch (_: Throwable) {
            null
        } finally {
            val currentDisplay = display
            if (currentDisplay != null) {
                try {
                    EGL14.eglMakeCurrent(
                        currentDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                } catch (_: Throwable) {
                    // 部分实现下这些常量本身为 null，忽略即可
                }
                try {
                    surface?.let { EGL14.eglDestroySurface(currentDisplay, it) }
                    context?.let { EGL14.eglDestroyContext(currentDisplay, it) }
                    EGL14.eglTerminate(currentDisplay)
                } catch (_: Throwable) {
                    // 清理失败不影响探测结果
                }
            }
        }
    }
}
