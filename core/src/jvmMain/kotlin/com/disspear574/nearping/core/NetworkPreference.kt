package com.disspear574.nearping.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class NetworkPreference(private val directory: Path) {
    private val path get() = directory.resolve("network-interface.txt")
    fun read(): String? = runCatching { Files.readString(path).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    fun write(name: String?) {
        require(name == null || (name.isNotBlank() && !name.contains('\n') && !name.contains('\r')))
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "network-interface-", ".tmp")
        try {
            Files.writeString(temporary, name ?: "")
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}
