package io.github.anderescaray.cents.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts text into the file the owner chose.
 *
 * It is one method with one job so that nothing above it has to know about `ContentResolver`,
 * and so the view model can be tested by handing it a different one. The `Uri` comes from the
 * system file picker, which is the whole reason this app asks for **no storage permission**:
 * the owner names a place, and the app is handed a door to that one file and nothing else.
 *
 * `"wt"` rather than `"w"`, and it matters: writing to an existing file without truncating it
 * leaves the tail of whatever was there before, so a second, smaller export would end in the
 * remains of the first — a backup that parses and lies.
 */
@Singleton
open class DocumentWriter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        open suspend fun write(
            uri: Uri,
            text: String,
        ) {
            withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                    "The picker returned a document that cannot be written to"
                }.use { stream -> stream.write(text.toByteArray()) }
            }
        }
    }
