package com.qeetgroup.qeetid.data.secure

import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.GeneralSecurityException

/**
 * Small files sealed by a [SecretBox], one value per file, written atomically. They live in the app's no-backup
 * directory (`Context.noBackupFilesDir`), which Android never copies into a backup or to another phone.
 *
 * Callers run these off the main thread.
 */
class SealedFiles(
    private val directory: File,
    private val box: SecretBox,
) {
    /** The file's value, or null when there is none — or when it can no longer be opened (see [Unreadable]). */
    fun read(name: String): ByteArray? {
        val file = AtomicFile(fileFor(name))
        val sealed =
            try {
                file.readFully()
            } catch (_: FileNotFoundException) {
                return null
            }
        return try {
            box.open(sealed, name)
        } catch (e: GeneralSecurityException) {
            // The key is gone (the app's data was restored onto another phone, or the Keystore was reset) or the file
            // was changed. Either way the value cannot be recovered; say so rather than crash.
            throw Unreadable(name, e)
        }
    }

    fun write(
        name: String,
        value: ByteArray,
    ) {
        directory.mkdirs()
        val file = AtomicFile(fileFor(name))
        val sealed = box.seal(value, name)
        val out = file.startWrite()
        try {
            out.write(sealed)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    fun delete(name: String) {
        AtomicFile(fileFor(name)).delete()
    }

    private fun fileFor(name: String): File {
        require(NAME.matches(name)) { "Not a sealed file name" }
        return File(directory, name)
    }

    /** A sealed file that exists but cannot be opened. */
    class Unreadable(
        val name: String,
        cause: Throwable,
    ) : Exception("Sealed file cannot be opened", cause)

    private companion object {
        val NAME = Regex("[a-z0-9.-]{1,80}")
    }
}
