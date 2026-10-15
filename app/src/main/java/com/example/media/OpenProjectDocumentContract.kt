package com.example.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Storage Access Framework contract that opens the Android system file / document picker
 * using Intent.ACTION_OPEN_DOCUMENT and Intent.CATEGORY_OPENABLE.
 *
 * Configured with wildcard MIME type and wrapped with Intent.createChooser so users can browse
 * phone storage and pick ZIP, 7Z, JSON, VN projects, or any archive format using Android's
 * Files manager or third-party file managers (such as ZArchiver), instead of launching
 * terminal applications like Termux.
 */
class OpenProjectDocumentContract : ActivityResultContract<Array<String>?, Uri?>() {

    override fun createIntent(context: Context, input: Array<String>?): Intent {
        val baseIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"

            // If specific MIME types are explicitly requested and do not contain */*,
            // provide them via EXTRA_MIME_TYPES
            val nonWildcardTypes = input?.filter { it.isNotBlank() && it != "*/*" }?.toTypedArray()
            if (!nonWildcardTypes.isNullOrEmpty()) {
                putExtra(Intent.EXTRA_MIME_TYPES, nonWildcardTypes)
            }
        }
        return Intent.createChooser(baseIntent, "Import Project File or Archive")
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}
