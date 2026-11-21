import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.HexFormat

val sdkVersion = "0.2.3"
val releaseRepository = "VitaDynamics/vbot-android-sdk-demo"
val checksums = mapOf(
    "vbot-sdk-$sdkVersion.aar" to "4ad3a56b41f547d94f9b11f4c55ef5ca72c87b027138356e80ad212b92acf88d",
    "vbot-sdk-$sdkVersion.pom" to "7748d166c17587b84dfe14d98daf4518e59c49d2a3ea5bc70b51f6d3de782712",
)

val mavenCache = gradle.gradleUserHomeDir.resolve("caches/vbot-release-maven")
val versionDirectory = mavenCache.resolve("com/vita/vbot/vbot-sdk/$sdkVersion")

fun matchesChecksum(file: File, expected: String): Boolean {
    if (!file.isFile) return false
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return HexFormat.of().formatHex(digest.digest()) == expected
}

if (checksums.any { (file, sha256) -> !matchesChecksum(versionDirectory.resolve(file), sha256) }) {
    check(!gradle.startParameter.isOffline) {
        "VBot SDK $sdkVersion is not cached. Run one build online with access to $releaseRepository."
    }
    Files.createDirectories(versionDirectory.toPath())
    val staging = Files.createTempDirectory(versionDirectory.toPath(), "release-").toFile()
    try {
        checksums.forEach { (name, sha256) ->
            val asset = staging.resolve(name)
            val url = URI.create("https://github.com/$releaseRepository/releases/download/v$sdkVersion/$name").toURL()
            try {
                val connection = url.openConnection().apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                }
                connection.getInputStream().use { input ->
                    asset.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (error: IOException) {
                throw GradleException("Could not download $name from $url", error)
            }
            check(matchesChecksum(asset, sha256)) { "VBot SDK checksum mismatch for $name" }
        }
        checksums.keys.forEach { name ->
            Files.move(staging.resolve(name).toPath(), versionDirectory.resolve(name).toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }
        logger.lifecycle("Fetched and verified VBot SDK $sdkVersion from GitHub Releases.")
    } finally {
        staging.deleteRecursively()
    }
}

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "VBotReleaseCache"
                    url = uri(mavenCache)
                }
            }
            filter { includeModule("com.vita.vbot", "vbot-sdk") }
        }
    }
}
