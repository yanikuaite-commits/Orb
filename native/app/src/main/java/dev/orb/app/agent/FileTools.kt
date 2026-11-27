package dev.orb.app.agent

import android.content.Context
import dev.orb.app.data.FileAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class FileTools(
    private val context: Context,
    private val approval: ApprovalCenter,
) {
    private val root: File by lazy { FilePaths.root() }

    fun hasAccess(): Boolean = FileAccess.hasAccess(context)

    suspend fun list(path: String): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val dir = resolve(path.ifBlank { "/sdcard" }) ?: return@io INVALID_PATH
        if (!dir.exists()) return@io "Não encontrei: ${display(dir)}"
        if (!dir.isDirectory) return@io describe(dir)
        val children = dir.listFiles()?.filterNot { FilePaths.isBlocked(root, it) }
            ?: return@io "Sem acesso à pasta ${display(dir)}."
        if (children.isEmpty()) return@io "A pasta ${display(dir)} está vazia."
        buildString {
            appendLine("Conteúdo de ${display(dir)} (${children.size} itens):")
            children
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                .take(MAX_LIST)
                .forEach { child ->
                    if (child.isDirectory) {
                        appendLine("- [pasta] ${child.name}")
                    } else {
                        appendLine("- [arquivo] ${child.name} (${formatSize(child.length())})")
                    }
                }
            if (children.size > MAX_LIST) appendLine("... mostrando $MAX_LIST de ${children.size} itens.")
        }
    }

    suspend fun find(query: String, path: String, maxResults: Int): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val term = query.trim()
        if (term.isEmpty()) return@io "Informe o nome ou parte do nome para procurar."
        val start = resolve(path.ifBlank { "/sdcard" }) ?: return@io INVALID_PATH
        if (!start.exists()) return@io "Não encontrei: ${display(start)}"
        val limit = maxResults.coerceIn(1, MAX_FIND)
        val found = mutableListOf<File>()
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(start to 0)
        while (stack.isNotEmpty() && found.size < limit) {
            val (current, depth) = stack.removeLast()
            if (current != start && current.name.contains(term, ignoreCase = true)) found += current
            if (current.isDirectory && depth < MAX_DEPTH) {
                current.listFiles()?.forEach { child ->
                    if (!isSymlink(child) && !FilePaths.isBlocked(root, child)) {
                        stack.addLast(child to depth + 1)
                    }
                }
            }
        }
        if (found.isEmpty()) return@io "Nenhum item com \"$term\" em ${display(start)}."
        buildString {
            appendLine("Encontrei ${found.size} item(ns) com \"$term\" em ${display(start)}:")
            found.forEach { file ->
                val suffix = if (file.isDirectory) " [pasta]" else " (${formatSize(file.length())})"
                appendLine("- ${display(file)}$suffix")
            }
            if (found.size >= limit) appendLine("Limite de $limit resultados atingido.")
        }
    }

    suspend fun info(path: String): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val file = resolve(path) ?: return@io INVALID_PATH
        if (!file.exists()) return@io "Não encontrei: ${display(file)}"
        describe(file)
    }

    suspend fun rename(path: String, newName: String, overwrite: Boolean): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val source = resolve(path) ?: return@io INVALID_PATH
        if (!source.exists()) return@io "Não encontrei: ${display(source)}"
        if (FilePaths.isRoot(root, source)) return@io "Não posso renomear a raiz do armazenamento."
        val name = newName.trim()
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name == "." || name == "..") {
            return@io "Nome inválido. Informe apenas o novo nome, sem barras."
        }
        val parent = source.parentFile ?: return@io "Não posso renomear este item."
        val target = runCatching { File(parent, name).canonicalFile }.getOrNull() ?: return@io INVALID_PATH
        if (resolve(target.path) != target) return@io INVALID_PATH
        if (target == source) return@io "O nome já é ${source.name}."
        if (target.exists() && !overwrite) {
            return@io "Já existe ${display(target)}. Use overwrite=true para substituir."
        }
        val replace = target.exists()
        val detail = if (replace) {
            "Substituir ${display(target)} por ${display(source)}?"
        } else {
            "Renomear ${display(source)} para ${display(target)}?"
        }
        if (!approve("Renomear item", detail, "MÉDIO", listOf(display(source), display(target)))) {
            return@io CANCELED
        }
        if (replace && !target.deleteRecursively()) return@io "Não consegui remover ${display(target)}."
        if (source.renameTo(target)) {
            audit("rename", "${display(source)} -> ${display(target)}", "ok")
            return@io "Renomeado: ${display(source)} -> ${display(target)}"
        }
        val copied = runCatching { copyRecursively(source, target, false) }
        if (copied.isFailure) return@io "Não consegui renomear: ${copied.exceptionOrNull()?.message ?: "erro"}"
        source.deleteRecursively()
        audit("rename", "${display(source)} -> ${display(target)}", "copiado e removido")
        "Renomeado: ${display(source)} -> ${display(target)}"
    }

    suspend fun move(sourcePath: String, destinationPath: String, overwrite: Boolean): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val source = resolve(sourcePath) ?: return@io INVALID_PATH
        val destination = resolve(destinationPath) ?: return@io INVALID_PATH
        if (!source.exists()) return@io "Não encontrei: ${display(source)}"
        if (FilePaths.isRoot(root, source)) return@io "Não posso mover a raiz do armazenamento."
        val target = if (destination.isDirectory) File(destination, source.name) else destination
        val canonicalTarget = runCatching { target.canonicalFile }.getOrNull() ?: return@io INVALID_PATH
        if (resolve(canonicalTarget.path) != canonicalTarget) return@io INVALID_PATH
        if (canonicalTarget == source) return@io "Origem e destino são o mesmo item."
        if (source.isDirectory && canonicalTarget.path.startsWith(source.path + File.separator)) {
            return@io "Não posso mover uma pasta para dentro dela mesma."
        }
        if (canonicalTarget.exists() && !overwrite) {
            return@io "Já existe ${display(canonicalTarget)}. Use overwrite=true para substituir."
        }
        val replace = canonicalTarget.exists()
        val detail = if (replace) {
            "Mover ${display(source)} e substituir ${display(canonicalTarget)}?"
        } else {
            "Mover ${display(source)} para ${display(canonicalTarget)}?"
        }
        if (!approve("Mover item", detail, "MÉDIO", listOf(display(source), display(canonicalTarget)))) {
            return@io CANCELED
        }
        if (replace && !canonicalTarget.deleteRecursively()) return@io "Não consegui remover ${display(canonicalTarget)}."
        if (!source.renameTo(canonicalTarget)) {
            val copied = runCatching { copyRecursively(source, canonicalTarget, false) }
            if (copied.isFailure) return@io "Não consegui mover: ${copied.exceptionOrNull()?.message ?: "erro"}"
            if (!source.deleteRecursively()) return@io "Copiei, mas não consegui remover a origem ${display(source)}."
        }
        audit("move", "${display(source)} -> ${display(canonicalTarget)}", "ok")
        "Movido: ${display(source)} -> ${display(canonicalTarget)}"
    }

    suspend fun copy(sourcePath: String, destinationPath: String, overwrite: Boolean): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val source = resolve(sourcePath) ?: return@io INVALID_PATH
        val destination = resolve(destinationPath) ?: return@io INVALID_PATH
        if (!source.exists()) return@io "Não encontrei: ${display(source)}"
        if (FilePaths.isRoot(root, source)) return@io "Não posso copiar a raiz do armazenamento."
        val target = if (destination.isDirectory) File(destination, source.name) else destination
        val canonicalTarget = runCatching { target.canonicalFile }.getOrNull() ?: return@io INVALID_PATH
        if (resolve(canonicalTarget.path) != canonicalTarget) return@io INVALID_PATH
        if (canonicalTarget == source) return@io "Origem e destino são o mesmo item."
        if (source.isDirectory && canonicalTarget.path.startsWith(source.path + File.separator)) {
            return@io "Não posso copiar uma pasta para dentro dela mesma."
        }
        if (canonicalTarget.exists()) {
            if (!overwrite) return@io "Já existe ${display(canonicalTarget)}. Use overwrite=true para substituir."
            if (!approve("Substituir destino", "Copiar ${display(source)} e substituir ${display(canonicalTarget)}?", "MÉDIO", listOf(display(canonicalTarget)))) {
                return@io CANCELED
            }
            if (!canonicalTarget.deleteRecursively()) return@io "Não consegui remover ${display(canonicalTarget)}."
        }
        val copied = runCatching { copyRecursively(source, canonicalTarget, false) }
        if (copied.isFailure) return@io "Não consegui copiar: ${copied.exceptionOrNull()?.message ?: "erro"}"
        audit("copy", "${display(source)} -> ${display(canonicalTarget)}", "ok")
        "Copiado: ${display(source)} -> ${display(canonicalTarget)}"
    }

    suspend fun delete(path: String): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val target = resolve(path) ?: return@io INVALID_PATH
        if (!target.exists()) return@io "Não encontrei: ${display(target)}"
        if (FilePaths.isRoot(root, target)) return@io "Não posso apagar a raiz do armazenamento."
        val (count, bytes) = measure(target)
        val detail = "Apagar ${display(target)}?\n$count arquivo(s), ${formatSize(bytes)}."
        if (!approve("Apagar arquivo", detail, "ALTO", listOf(display(target)))) return@io CANCELED
        val ok = target.deleteRecursively()
        audit("delete", display(target), if (ok) "ok" else "parcial")
        if (ok) {
            "Apagado: ${display(target)} ($count arquivo(s), ${formatSize(bytes)})"
        } else {
            "Não consegui apagar tudo em ${display(target)}."
        }
    }

    suspend fun makeFolder(path: String): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val target = resolve(path) ?: return@io INVALID_PATH
        if (target.exists()) {
            return@io if (target.isDirectory) {
                "A pasta já existe: ${display(target)}"
            } else {
                "Já existe um arquivo com esse nome: ${display(target)}"
            }
        }
        if (!target.mkdirs()) return@io "Não consegui criar ${display(target)}."
        audit("mkdir", display(target), "ok")
        "Pasta criada: ${display(target)}"
    }

    suspend fun zip(sources: String, destination: String, overwrite: Boolean): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val paths = sources.split('\n', '|').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (paths.isEmpty()) return@io "Informe pelo menos um caminho para compactar."
        val resolved = paths.map { resolve(it) ?: return@io INVALID_PATH }
        resolved.forEach { if (!it.exists()) return@io "Não encontrei: ${display(it)}" }
        val dest = resolve(destination) ?: return@io INVALID_PATH
        if (resolved.any { it == dest }) return@io "O ZIP de destino não pode ser uma das origens."
        if (dest.isDirectory) return@io "O destino deve ser um arquivo .zip."
        if (dest.exists()) {
            if (!overwrite) return@io "Já existe ${display(dest)}. Use overwrite=true para substituir."
            if (!approve("Substituir ZIP", "Substituir ${display(dest)}?", "MÉDIO", listOf(display(dest)))) {
                return@io CANCELED
            }
            if (!dest.delete()) return@io "Não consegui remover ${display(dest)}."
        }
        dest.parentFile?.mkdirs()
        var entries = 0
        ZipOutputStream(BufferedOutputStream(FileOutputStream(dest))).use { zos ->
            fun add(file: File, base: File) {
                if (isSymlink(file)) return
                if (FilePaths.isBlocked(root, file)) return
                if (file.canonicalFile == dest.canonicalFile) return
                if (entries >= MAX_ZIP_ENTRIES) throw IOException("ZIP com entradas demais.")
                val name = runCatching {
                    base.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                }.getOrElse { file.name }
                if (name.startsWith("../")) return
                if (name.isBlank()) {
                    if (file.isDirectory) file.listFiles()?.forEach { child -> add(child, base) }
                    return
                }
                if (file.isDirectory) {
                    val entryName = if (name.endsWith("/")) name else "$name/"
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.closeEntry()
                    entries++
                    file.listFiles()?.forEach { child -> add(child, base) }
                } else {
                    zos.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                    FileInputStream(file).use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                    entries++
                }
            }
            resolved.forEach { source ->
                if (!source.exists()) throw IOException("Não encontrei ${display(source)}")
                val base = if (FilePaths.isRoot(root, source)) source else (source.parentFile ?: root)
                add(source, base)
            }
        }
        audit("zip", "${resolved.joinToString { display(it) }} -> ${display(dest)}", "$entries entradas")
        "ZIP criado: ${display(dest)} ($entries entrada(s), ${formatSize(dest.length())})"
    }

    suspend fun unzip(zipPath: String, destination: String, overwrite: Boolean): String = io {
        if (!hasAccess()) return@io NO_ACCESS
        val zip = resolve(zipPath) ?: return@io INVALID_PATH
        if (!zip.isFile) return@io "Não encontrei o ZIP: ${display(zip)}"
        val dest = resolve(destination) ?: return@io INVALID_PATH
        if (dest.exists() && !dest.isDirectory) return@io "O destino precisa ser uma pasta: ${display(dest)}"
        val entries = mutableListOf<ZipEntry>()
        var total = 0L
        ZipFile(zip).use { zf ->
            val enumeration = zf.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                if (entry.isDirectory) continue
                val target = FilePaths.safeChild(dest, entry.name)
                    ?: return@io "ZIP inválido: entrada suspeita (${entry.name})."
                if (FilePaths.isBlocked(root, target)) {
                    return@io "ZIP contém caminho bloqueado (${entry.name})."
                }
                if (target.exists() && !overwrite) {
                    return@io "Já existe ${display(target)}. Use overwrite=true para substituir."
                }
                if (entry.size > MAX_ENTRY_BYTES) return@io "Entrada grande demais: ${entry.name}."
                total += entry.size.coerceAtLeast(0L)
                if (total > MAX_UNZIP_BYTES) return@io "ZIP grande demais para extrair."
                if (entries.size >= MAX_ZIP_ENTRIES) return@io "ZIP com entradas demais."
                entries += entry
            }
            if (entries.isEmpty()) return@io "O ZIP não tem arquivos."
            val detail = "Extrair ${entries.size} arquivo(s) de ${display(zip)} para ${display(dest)}?"
            if (!approve("Extrair ZIP", detail, "MÉDIO", listOf(display(zip), display(dest)))) {
                return@io CANCELED
            }
            dest.mkdirs()
            entries.forEach { entry ->
                val target = FilePaths.safeChild(dest, entry.name)
                    ?: return@io "ZIP inválido: entrada suspeita (${entry.name})."
                target.parentFile?.mkdirs()
                if (target.isDirectory) target.deleteRecursively()
                zf.getInputStream(entry).use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
        }
        audit("unzip", "${display(zip)} -> ${display(dest)}", "${entries.size} arquivos")
        "Extraído: ${entries.size} arquivo(s) em ${display(dest)}"
    }

    private fun describe(file: File): String {
        val kind = if (file.isDirectory) "pasta" else "arquivo"
        val size = if (file.isFile) formatSize(file.length()) else "-"
        val modified = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
            .format(Date(file.lastModified()))
        return buildString {
            appendLine("Caminho: ${display(file)}")
            appendLine("Tipo: $kind")
            appendLine("Tamanho: $size")
            appendLine("Modificado: $modified")
            append("Legível: ${if (file.canRead()) "sim" else "não"} · Gravável: ${if (file.canWrite()) "sim" else "não"}")
        }
    }

    private fun measure(file: File): Pair<Int, Long> {
        if (!file.exists() || isSymlink(file)) return 0 to 0L
        if (file.isFile) return 1 to file.length()
        var count = 0
        var bytes = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(file)
        while (stack.isNotEmpty() && count < MAX_MEASURE_FILES) {
            val current = stack.removeLast()
            if (isSymlink(current)) continue
            if (current.isDirectory) {
                current.listFiles()?.forEach { stack.addLast(it) }
            } else {
                count++
                bytes += current.length()
            }
        }
        return count to bytes
    }

    private fun copyRecursively(source: File, target: File, overwrite: Boolean) {
        if (isSymlink(source)) throw IOException("link simbólico ignorado: ${source.name}")
        if (isSymlink(target)) throw IOException("destino é um link simbólico: ${target.path}")
        if (source.isDirectory) {
            if (target.exists() && !target.isDirectory) {
                throw IOException("destino é um arquivo: ${target.path}")
            }
            if (!target.exists() && !target.mkdirs()) {
                throw IOException("não consegui criar ${target.path}")
            }
            source.listFiles()?.forEach { child ->
                copyRecursively(child, File(target, child.name), overwrite)
            }
        } else {
            if (target.exists()) {
                if (!overwrite) throw IOException("destino já existe: ${target.path}")
                if (!target.deleteRecursively()) throw IOException("não consegui substituir ${target.path}")
            }
            target.parentFile?.mkdirs()
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            target.setLastModified(source.lastModified())
        }
    }

    private fun isSymlink(file: File): Boolean =
        runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)

    private fun resolve(input: String): File? = FilePaths.resolve(root, input)

    private fun display(file: File): String = FilePaths.display(root, file)

    private suspend fun approve(
        title: String,
        detail: String,
        risk: String,
        paths: List<String>,
    ): Boolean = approval.request(FileApproval(UUID.randomUUID().toString(), title, detail, risk, paths))

    private fun audit(action: String, detail: String, result: String) {
        runCatching {
            val log = File(context.filesDir, "orb/file_audit.log")
            log.parentFile?.mkdirs()
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            log.appendText("$stamp\t$action\t$detail\t$result\n")
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / (1L shl 10).toDouble())
        else -> "$bytes B"
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val MAX_LIST = 200
        const val MAX_FIND = 100
        const val MAX_DEPTH = 8
        const val MAX_ZIP_ENTRIES = 10_000
        const val MAX_MEASURE_FILES = 100_000
        const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
        const val MAX_UNZIP_BYTES = 1024L * 1024 * 1024
        const val NO_ACCESS = "Sem permissão de arquivos. Ative \"Acesso a todos os arquivos\" em Ajustes → Arquivos."
        const val INVALID_PATH = "Caminho inválido. Use /sdcard/... ou um caminho relativo; /sdcard/Android é bloqueada."
        const val CANCELED = "Operação cancelada pelo usuário."
    }
}
