package com.example.media

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.AspectRatio
import com.example.data.model.InterpolationType
import com.example.data.model.Keyframe
import com.example.data.model.Project
import com.example.data.model.TimelineClip
import com.example.data.model.VNImportReport
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ArchiveImporter {

    private const val TAG = "ArchiveImporter"

    enum class DetectedArchiveType(val label: String, val isArchive: Boolean) {
        SEVEN_ZIP("7-Zip Archive (.7z)", true),
        ZIP("ZIP Archive (.zip)", true),
        VN_ARCHIVE("VN Project Package (.vnproj)", true),
        JSON_PROJECT("JSON Project File (.json)", false),
        COPY_EDIT_NATIVE("COPY EDIT Project File", false),
        UNKNOWN_FORMAT("Unknown File Format", false)
    }

    /**
     * Safely inspects, streams, and imports a project file or archive (ZIP, 7Z, .vnproj, JSON, or media packages).
     *
     * 1. Acquires persistable permission safely.
     * 2. Reads via ContentResolver.openInputStream(uri) directly without assuming a fake filesystem path.
     * 3. Streams to an app-private staging file with live progress.
     * 4. Detects archive format via header magic bytes and extension.
     * 5. Safely extracts ZIP and 7Z archives with strict canonical path traversal validation.
     * 6. Discovers project files (VN, COPY EDIT, or generic JSON) and media assets.
     * 7. Moves media assets to app-private storage so they persist reliably.
     * 8. Generates a comprehensive VNImportReport diagnostic summary.
     */
    fun importArchiveOrFile(
        context: Context,
        uri: Uri,
        metadata: DocumentMetadata? = null,
        onProgress: ((progress: Float, status: String, detectedFormat: String) -> Unit)? = null
    ): VNImportReport {
        // Step 1: Query metadata if not provided
        val meta = metadata ?: DocumentUtils.queryDocumentMetadata(context, uri)
        val fileName = meta.displayName
        val fileSizeBytes = meta.sizeBytes

        // Step 2: Try to take persistable URI permission
        DocumentUtils.takePersistableUriPermissionSafely(context, uri)

        onProgress?.invoke(0.05f, "Reading document stream from storage...", "Detecting format...")

        // Destination directories in app-private storage
        val importId = UUID.randomUUID().toString()
        val stagingFile = File(context.cacheDir, "staging_$importId.tmp")
        val extractDir = File(context.filesDir, "imported_projects/$importId").apply { mkdirs() }

        return try {
            // Step 3: Stream from ContentResolver to app-private cache
            val openStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (openStream == null) {
                return errorReport(
                    fileName = fileName,
                    fileSize = fileSizeBytes,
                    format = "Unknown",
                    isArchive = false,
                    error = "Could not open input stream via ContentResolver for $uri"
                )
            }

            openStream.use { input ->
                FileOutputStream(stagingFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (fileSizeBytes > 0) {
                            val streamProgress = (bytesCopied.toFloat() / fileSizeBytes).coerceIn(0f, 0.45f)
                            onProgress?.invoke(
                                streamProgress,
                                "Reading document (${DocumentUtils.formatFileSize(bytesCopied)} / ${DocumentUtils.formatFileSize(fileSizeBytes)})...",
                                "Reading stream..."
                            )
                        } else {
                            onProgress?.invoke(
                                0.25f,
                                "Reading document stream (${DocumentUtils.formatFileSize(bytesCopied)})...",
                                "Reading stream..."
                            )
                        }
                    }
                }
            }

            // Step 4: Detect archive / file format via magic bytes
            val detectedType = detectFormat(stagingFile, fileName)
            val actualFileSize = if (fileSizeBytes > 0) fileSizeBytes else stagingFile.length()
            onProgress?.invoke(0.50f, "Detected ${detectedType.label}. Extracting contents...", detectedType.label)

            val extractedFiles = mutableListOf<File>()

            when (detectedType) {
                DetectedArchiveType.SEVEN_ZIP -> {
                    extract7zSafely(stagingFile, extractDir, extractedFiles) { progressFraction, entryName ->
                        val overall = 0.50f + (progressFraction * 0.30f)
                        onProgress?.invoke(
                            overall,
                            "Extracting 7z: $entryName",
                            detectedType.label
                        )
                    }
                }
                DetectedArchiveType.ZIP,
                DetectedArchiveType.VN_ARCHIVE -> {
                    extractZipSafely(stagingFile, extractDir, extractedFiles) { progressFraction, entryName ->
                        val overall = 0.50f + (progressFraction * 0.30f)
                        onProgress?.invoke(
                            overall,
                            "Extracting: $entryName",
                            detectedType.label
                        )
                    }
                }
                DetectedArchiveType.JSON_PROJECT,
                DetectedArchiveType.COPY_EDIT_NATIVE,
                DetectedArchiveType.UNKNOWN_FORMAT -> {
                    // Try parsing as JSON or direct project file
                    val targetFile = File(extractDir, fileName)
                    stagingFile.copyTo(targetFile, overwrite = true)
                    extractedFiles.add(targetFile)
                }
            }

            // Clean staging file
            stagingFile.delete()

            onProgress?.invoke(0.85f, "Analyzing tracks, cuts, and keyframes...", detectedType.label)

            // Step 5: Locate project descriptor (.json, .vnproj, XML)
            val projectFile = extractedFiles.firstOrNull {
                it.name.endsWith(".json", ignoreCase = true) ||
                it.name.endsWith(".vnproj", ignoreCase = true) ||
                it.name.contains("project", ignoreCase = true)
            }

            // Locate media assets
            val mediaFiles = extractedFiles.filter {
                it.extension.lowercase(Locale.ROOT) in listOf("mp4", "m4v", "mov", "mkv", "webm", "mp3", "wav", "m4a", "aac")
            }

            onProgress?.invoke(0.95f, "Finalizing project...", detectedType.label)

            val report = if (projectFile != null && projectFile.exists()) {
                parseProjectFile(
                    context = context,
                    projectFile = projectFile,
                    mediaFiles = mediaFiles,
                    sourceFileName = fileName,
                    fileSizeBytes = actualFileSize,
                    detectedFormat = detectedType.label,
                    isArchive = detectedType.isArchive
                )
            } else if (mediaFiles.isNotEmpty()) {
                buildSequentialMediaProject(
                    context = context,
                    mediaFiles = mediaFiles,
                    sourceFileName = fileName,
                    fileSizeBytes = actualFileSize,
                    detectedFormat = detectedType.label,
                    isArchive = detectedType.isArchive
                )
            } else {
                VNImportReport(
                    sourceFileName = fileName,
                    isArchive = detectedType.isArchive,
                    fileSizeBytes = actualFileSize,
                    detectedFormat = detectedType.label,
                    recognizedClips = 0,
                    recognizedCuts = 0,
                    recognizedKeyframes = 0,
                    recognizedAudioTracks = 0,
                    supportedEffects = emptyList(),
                    unsupportedEffects = emptyList(),
                    warnings = listOf("Archive extracted safely, but no recognized project descriptor (.json, .vnproj) or media clips were found."),
                    importedProject = null
                )
            }

            onProgress?.invoke(1.0f, "Import complete!", detectedType.label)
            report
        } catch (e: Exception) {
            Log.e(TAG, "Import failed for $fileName", e)
            stagingFile.delete()
            errorReport(
                fileName = fileName,
                fileSize = fileSizeBytes,
                format = "Import Error",
                isArchive = fileName.endsWith(".zip", true) || fileName.endsWith(".7z", true),
                error = "Extraction or parse error: ${e.message}"
            )
        }
    }

    /**
     * Determines archive format using both header magic bytes and file extension.
     */
    private fun detectFormat(file: File, originalName: String): DetectedArchiveType {
        val header = ByteArray(8)
        val bytesRead = try {
            FileInputStream(file).use { it.read(header) }
        } catch (_: Exception) {
            0
        }

        // 7-Zip magic bytes: 37 7A BC AF 27 1C
        if (bytesRead >= 6 &&
            header[0] == 0x37.toByte() &&
            header[1] == 0x7A.toByte() &&
            header[2] == 0xBC.toByte() &&
            header[3] == 0xAF.toByte() &&
            header[4] == 0x27.toByte() &&
            header[5] == 0x1C.toByte()
        ) {
            return DetectedArchiveType.SEVEN_ZIP
        }

        // ZIP magic bytes: PK (50 4B 03 04, 50 4B 05 06, 50 4B 07 08)
        if (bytesRead >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
            return if (originalName.endsWith(".vnproj", ignoreCase = true)) {
                DetectedArchiveType.VN_ARCHIVE
            } else {
                DetectedArchiveType.ZIP
            }
        }

        // JSON check: begins with '{' or '[' after whitespace
        try {
            FileInputStream(file).bufferedReader().use { reader ->
                var ch = reader.read()
                while (ch != -1 && (ch.toChar().isWhitespace() || ch == 0xFEFF.toInt())) {
                    ch = reader.read()
                }
                if (ch == '{'.code || ch == '['.code) {
                    return if (originalName.contains("copyedit", ignoreCase = true)) {
                        DetectedArchiveType.COPY_EDIT_NATIVE
                    } else {
                        DetectedArchiveType.JSON_PROJECT
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback by extension
        val ext = originalName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "7z" -> DetectedArchiveType.SEVEN_ZIP
            "zip" -> DetectedArchiveType.ZIP
            "vnproj" -> DetectedArchiveType.VN_ARCHIVE
            "json" -> DetectedArchiveType.JSON_PROJECT
            "copyedit" -> DetectedArchiveType.COPY_EDIT_NATIVE
            else -> DetectedArchiveType.UNKNOWN_FORMAT
        }
    }

    /**
     * Safely extracts 7Z archives using Apache Commons Compress SevenZFile.
     * Prevents Zip Slip / path traversal vulnerabilities.
     */
    private fun extract7zSafely(
        archiveFile: File,
        destDir: File,
        outFiles: MutableList<File>,
        onEntryProgress: ((fraction: Float, name: String) -> Unit)? = null
    ) {
        val canonicalDestDirPath = destDir.canonicalPath

        SevenZFile.builder().setFile(archiveFile).get().use { sevenZFile ->
            var entry = sevenZFile.nextEntry
            var entryIndex = 0

            while (entry != null) {
                entryIndex++
                val entryName = entry.name.replace('\\', '/')
                val newFile = File(destDir, entryName)
                val canonicalNewFilePath = newFile.canonicalPath

                // Zip Slip protection
                if (!canonicalNewFilePath.startsWith(canonicalDestDirPath + File.separator)) {
                    throw SecurityException("Security violation: 7z entry '$entryName' attempts path traversal.")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (sevenZFile.read(buffer).also { count = it } > 0) {
                            fos.write(buffer, 0, count)
                        }
                    }
                    outFiles.add(newFile)
                }

                onEntryProgress?.invoke((entryIndex % 20) / 20f, newFile.name)
                entry = sevenZFile.nextEntry
            }
        }
    }

    /**
     * Safely extracts ZIP archives using ZipInputStream.
     * Prevents Zip Slip / path traversal vulnerabilities.
     */
    private fun extractZipSafely(
        archiveFile: File,
        destDir: File,
        outFiles: MutableList<File>,
        onEntryProgress: ((fraction: Float, name: String) -> Unit)? = null
    ) {
        val canonicalDestDirPath = destDir.canonicalPath

        ZipInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            var entryIndex = 0

            while (entry != null) {
                entryIndex++
                val entryName = entry.name.replace('\\', '/')
                val newFile = File(destDir, entryName)
                val canonicalNewFilePath = newFile.canonicalPath

                // Zip Slip protection
                if (!canonicalNewFilePath.startsWith(canonicalDestDirPath + File.separator)) {
                    throw SecurityException("Security violation: Zip entry '$entryName' attempts path traversal.")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    outFiles.add(newFile)
                }

                onEntryProgress?.invoke((entryIndex % 20) / 20f, newFile.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Parses project definitions (supporting VN format, COPY EDIT native JSON, and generic descriptors).
     */
    private fun parseProjectFile(
        context: Context,
        projectFile: File,
        mediaFiles: List<File>,
        sourceFileName: String,
        fileSizeBytes: Long,
        detectedFormat: String,
        isArchive: Boolean
    ): VNImportReport {
        val rawJson = projectFile.readText()
        val warnings = mutableListOf<String>()
        val supportedEffects = mutableListOf<String>()
        val unsupportedEffects = mutableListOf<String>()

        return try {
            val root = JSONObject(rawJson)
            val title = root.optString("name", root.optString("title", sourceFileName.substringBeforeLast('.')))
            val aspectRatioStr = root.optString("aspectRatio", "16:9")
            val aspectRatio = AspectRatio.fromLabel(aspectRatioStr)

            val clipsList = mutableListOf<TimelineClip>()
            var recognizedKeyframesCount = 0
            var recognizedCutsCount = 0
            var recognizedAudioCount = 0

            // Support both standard arrays: "clips", "tracks", "materials", "segments"
            val clipsArray = root.optJSONArray("clips")
                ?: root.optJSONArray("segments")
                ?: root.optJSONArray("materials")
                ?: JSONArray()

            var currentTimelineMs = 0L

            for (i in 0 until clipsArray.length()) {
                val clipObj = clipsArray.optJSONObject(i) ?: continue
                val clipDuration = clipObj.optLong("duration", clipObj.optLong("durationMs", 3000L))
                val trimIn = clipObj.optLong("trimStart", clipObj.optLong("trimStartMs", clipObj.optLong("inPoint", 0L)))
                val trimOut = clipObj.optLong("trimEnd", clipObj.optLong("trimEndMs", clipObj.optLong("outPoint", clipDuration)))
                val clipName = clipObj.optString("name", clipObj.optString("displayName", "Clip ${i + 1}"))
                val speed = clipObj.optDouble("speed", 1.0).toFloat()

                // Check media reference
                val sourceRef = clipObj.optString("sourceUri", clipObj.optString("source", clipObj.optString("path", "")))
                val sourceFileNameOnly = sourceRef.substringAfterLast('/').substringAfterLast('\\')
                val matchedMediaFile = mediaFiles.firstOrNull {
                    it.name.equals(sourceRef, ignoreCase = true) ||
                    it.name.equals(sourceFileNameOnly, ignoreCase = true)
                }

                val resolvedUri = when {
                    matchedMediaFile != null -> Uri.fromFile(matchedMediaFile).toString()
                    sourceRef.isNotBlank() && File(sourceRef).exists() -> Uri.fromFile(File(sourceRef)).toString()
                    sourceRef.startsWith("content://") || sourceRef.startsWith("android.resource://") -> sourceRef
                    else -> {
                        warnings.add("Media file '${sourceFileNameOnly.ifBlank { "unnamed" }}' for '$clipName' not bundled in archive; linked to sample preview media.")
                        SampleMediaProvider.getSampleMotionUri(context)
                    }
                }

                // Parse keyframes
                val keyframes = mutableListOf<Keyframe>()
                val kfArray = clipObj.optJSONArray("keyframes")
                if (kfArray != null) {
                    for (k in 0 until kfArray.length()) {
                        val kfObj = kfArray.optJSONObject(k) ?: continue
                        val time = kfObj.optLong("timestampMs", kfObj.optLong("time", 0L))
                        val scaleX = kfObj.optDouble("scaleX", kfObj.optDouble("scale", 1.0)).toFloat()
                        val scaleY = kfObj.optDouble("scaleY", kfObj.optDouble("scale", 1.0)).toFloat()
                        val rot = kfObj.optDouble("rotation", 0.0).toFloat()
                        val posX = kfObj.optDouble("posX", 0.0).toFloat()
                        val posY = kfObj.optDouble("posY", 0.0).toFloat()
                        val opacity = kfObj.optDouble("opacity", 1.0).toFloat()
                        val curveStr = kfObj.optString("interpolation", kfObj.optString("curve", "LINEAR"))

                        keyframes.add(
                            Keyframe(
                                timestampMs = time,
                                posX = posX,
                                posY = posY,
                                scaleX = scaleX,
                                scaleY = scaleY,
                                rotation = rot,
                                opacity = opacity,
                                interpolation = InterpolationType.fromString(curveStr)
                            )
                        )
                        recognizedKeyframesCount++
                    }
                }

                if (keyframes.isNotEmpty()) {
                    supportedEffects.add("Transform Keyframes (Clip ${i + 1})")
                }

                // Inspect proprietary effects
                if (clipObj.has("lutFilter") || clipObj.has("colorFilter")) {
                    unsupportedEffects.add("Custom LUT Color Grading (${clipObj.optString("lutFilter")})")
                }
                if (clipObj.has("chromaKey")) {
                    unsupportedEffects.add("Chroma Key Green Screen")
                }
                if (clipObj.has("curveSpeed") || clipObj.has("speedRamping")) {
                    unsupportedEffects.add("Nonlinear Speed Ramping (reverted to constant ${speed}x)")
                }

                val clipDurationReal = (trimOut - trimIn).coerceAtLeast(500L)
                clipsList.add(
                    TimelineClip(
                        id = UUID.randomUUID().toString(),
                        sourceUri = resolvedUri,
                        displayName = clipName,
                        sourceDurationMs = clipDuration,
                        trimStartMs = trimIn,
                        trimEndMs = trimOut,
                        timelineStartMs = currentTimelineMs,
                        speed = speed,
                        keyframes = keyframes
                    )
                )
                currentTimelineMs += clipDurationReal
                recognizedCutsCount++
            }

            if (clipsList.isEmpty() && mediaFiles.isNotEmpty()) {
                return buildSequentialMediaProject(context, mediaFiles, sourceFileName, fileSizeBytes, detectedFormat, isArchive)
            }

            val project = Project(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                aspectRatio = aspectRatio,
                durationMs = currentTimelineMs,
                clips = clipsList
            )

            supportedEffects.add("Timeline Sequence & Trimming")
            if (mediaFiles.isNotEmpty()) {
                supportedEffects.add("Bundled Local Media (${mediaFiles.size} assets)")
            }

            VNImportReport(
                sourceFileName = sourceFileName,
                isArchive = isArchive,
                fileSizeBytes = fileSizeBytes,
                detectedFormat = detectedFormat,
                recognizedClips = clipsList.size,
                recognizedCuts = recognizedCutsCount,
                recognizedKeyframes = recognizedKeyframesCount,
                recognizedAudioTracks = recognizedAudioCount,
                supportedEffects = supportedEffects.distinct(),
                unsupportedEffects = unsupportedEffects.distinct(),
                warnings = warnings,
                importedProject = project
            )
        } catch (e: Exception) {
            warnings.add("Project descriptor had non-standard format: ${e.message}. Fallback to media file discovery.")
            if (mediaFiles.isNotEmpty()) {
                buildSequentialMediaProject(context, mediaFiles, sourceFileName, fileSizeBytes, detectedFormat, isArchive, warnings)
            } else {
                VNImportReport(
                    sourceFileName = sourceFileName,
                    isArchive = isArchive,
                    fileSizeBytes = fileSizeBytes,
                    detectedFormat = detectedFormat,
                    recognizedClips = 0,
                    recognizedCuts = 0,
                    recognizedKeyframes = 0,
                    recognizedAudioTracks = 0,
                    supportedEffects = emptyList(),
                    unsupportedEffects = listOf("Proprietary/Encrypted VN Project Schema"),
                    warnings = listOf("Could not parse '${projectFile.name}': ${e.message}"),
                    importedProject = null
                )
            }
        }
    }

    private fun buildSequentialMediaProject(
        context: Context,
        mediaFiles: List<File>,
        sourceFileName: String,
        fileSizeBytes: Long,
        detectedFormat: String,
        isArchive: Boolean,
        additionalWarnings: List<String> = emptyList()
    ): VNImportReport {
        val clips = mutableListOf<TimelineClip>()
        var timelineOffset = 0L

        for ((index, file) in mediaFiles.withIndex()) {
            val uri = Uri.fromFile(file).toString()
            val meta = SampleMediaProvider.inspectVideo(context, uri)
            val clipDuration = meta.durationMs.coerceAtLeast(2000L)

            clips.add(
                TimelineClip(
                    id = UUID.randomUUID().toString(),
                    sourceUri = uri,
                    displayName = file.nameWithoutExtension,
                    sourceDurationMs = clipDuration,
                    trimStartMs = 0L,
                    trimEndMs = clipDuration,
                    timelineStartMs = timelineOffset
                )
            )
            timelineOffset += clipDuration
        }

        val project = Project(
            id = UUID.randomUUID().toString(),
            title = sourceFileName.substringBeforeLast("."),
            aspectRatio = AspectRatio.RATIO_16_9,
            durationMs = timelineOffset,
            clips = clips
        )

        return VNImportReport(
            sourceFileName = sourceFileName,
            isArchive = isArchive,
            fileSizeBytes = fileSizeBytes,
            detectedFormat = detectedFormat,
            recognizedClips = clips.size,
            recognizedCuts = clips.size,
            recognizedKeyframes = 0,
            recognizedAudioTracks = 1,
            supportedEffects = listOf("Sequenced Multi-Clip Timeline"),
            unsupportedEffects = emptyList(),
            warnings = additionalWarnings + listOf("Extracted ${mediaFiles.size} media clip(s) and structured them into a continuous timeline."),
            importedProject = project
        )
    }

    private fun errorReport(
        fileName: String,
        fileSize: Long,
        format: String,
        isArchive: Boolean,
        error: String
    ): VNImportReport {
        return VNImportReport(
            sourceFileName = fileName,
            isArchive = isArchive,
            fileSizeBytes = fileSize,
            detectedFormat = format,
            recognizedClips = 0,
            recognizedCuts = 0,
            recognizedKeyframes = 0,
            recognizedAudioTracks = 0,
            supportedEffects = emptyList(),
            unsupportedEffects = emptyList(),
            warnings = listOf(error),
            importedProject = null
        )
    }
}
