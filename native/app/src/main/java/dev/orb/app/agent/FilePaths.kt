package dev.orb.app.agent

import android.os.Environment
import java.io.File

object FilePaths {
    fun root(): File = Environment.getExternalStorageDirectory().canonicalFile

    fun resolve(root: File, input: String): File? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val candidate = when {
            trimmed == "/sdcard" || trimmed == "sdcard" -> rootCanonical
            trimmed.startsWith("/sdcard/") -> File(rootCanonical, trimmed.removePrefix("/sdcard/"))
            trimmed.startsWith("sdcard/") -> File(rootCanonical, trimmed.removePrefix("sdcard/"))
            trimmed == rootCanonical.path -> rootCanonical
            trimmed.startsWith(rootCanonical.path + File.separator) -> File(trimmed)
            trimmed.startsWith("/") -> File(trimmed)
            else -> File(rootCanonical, trimmed)
        }
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (canonical != rootCanonical && !canonical.path.startsWith(rootCanonical.path + File.separator)) {
            return null
        }
        if (isBlocked(rootCanonical, canonical)) return null
        return canonical
    }

    fun isBlocked(root: File, file: File): Boolean {
        val android = runCatching { File(root, "Android").canonicalFile }.getOrNull() ?: return true
        return file == android || file.path.startsWith(android.path + File.separator)
    }

    fun isRoot(root: File, file: File): Boolean =
        runCatching { file.canonicalFile == root.canonicalFile }.getOrDefault(false)

    fun display(root: File, file: File): String {
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return file.path
        if (file == rootCanonical) return "/sdcard"
        val prefix = rootCanonical.path + File.separator
        return if (file.path.startsWith(prefix)) {
            "/sdcard/" + file.path.removePrefix(prefix)
        } else {
            file.path
        }
    }

    fun safeChild(root: File, name: String): File? {
        val clean = name.replace('\\', '/')
        if (clean.isBlank() || clean.startsWith("/")) return null
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { File(rootCanonical, clean).canonicalFile }.getOrNull() ?: return null
        if (target != rootCanonical && !target.path.startsWith(rootCanonical.path + File.separator)) {
            return null
        }
        return target
    }
}
