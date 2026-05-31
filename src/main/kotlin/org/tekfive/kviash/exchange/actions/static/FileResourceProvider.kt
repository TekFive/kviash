package org.tekfive.kviash.exchange.actions.`static`

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class FileResourceProvider(
    private val baseDir: File,
) : ResourceProvider {
    private val basePath: Path = baseDir.toPath().toAbsolutePath().normalize()

    override fun get(urlPrefix: String, resourcePath: String): ByteArray? {
        val requestedPath = basePath.resolve(resourcePath.trimStart('/')).normalize()
        if (!requestedPath.startsWith(basePath)) return null
        if (containsSymlink(requestedPath)) return null
        if (!Files.isRegularFile(requestedPath)) return null
        return Files.readAllBytes(requestedPath)
    }

    private fun containsSymlink(requestedPath: Path): Boolean {
        val relativePath = basePath.relativize(requestedPath)
        var currentPath = basePath

        for (segment in relativePath) {
            currentPath = currentPath.resolve(segment)
            if (Files.isSymbolicLink(currentPath)) {
                return true
            }
        }

        return false
    }
}
