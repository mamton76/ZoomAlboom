package com.mamton.zoomalbum.data.local.file

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class FileStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun sceneFile(albumId: Long): File =
        File(context.filesDir, "scene_$albumId.json")

    fun read(albumId: Long): String? {
        val file = sceneFile(albumId)
        return if (file.exists()) file.readText() else null
    }

    fun write(albumId: Long, content: String) {
        sceneFile(albumId).writeText(content)
    }
}
