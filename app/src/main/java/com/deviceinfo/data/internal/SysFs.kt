package com.deviceinfo.data.internal

import java.io.File

/**
 * 讀取 /sys 與 /proc 的小工具。
 *
 * 國內 ROM（HyperOS / ColorOS 等）對部分節點做了 SELinux 限制，
 * 因此所有讀取必須「讀不到就返回 null」，絕不讓異常穿透到 UI 層。
 */
internal object SysFs {

    fun readText(path: String): String? = try {
        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            null
        } else {
            file.readText().trim().takeIf { it.isNotEmpty() }
        }
    } catch (_: Throwable) {
        null
    }

    fun readLong(path: String): Long? = readText(path)?.toLongOrNull()

    fun readInt(path: String): Int? = readText(path)?.toLongOrNull()?.toInt()

    fun readFirstLine(path: String): String? =
        readText(path)?.lineSequence()?.firstOrNull()?.trim()

    /** 依次嘗試多個路徑，返回第一個可讀值。 */
    fun firstInt(vararg paths: String): Int? {
        for (path in paths) {
            readInt(path)?.let { return it }
        }
        return null
    }

    fun firstText(vararg paths: String): String? {
        for (path in paths) {
            readText(path)?.let { return it }
        }
        return null
    }
}
