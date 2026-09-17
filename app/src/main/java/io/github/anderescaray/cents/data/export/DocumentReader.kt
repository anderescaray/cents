package io.github.anderescaray.cents.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the text of the file the owner chose.
 *
 * The mirror of [DocumentWriter], and there for the same reason: nothing above it has to know
 * about `ContentResolver`, and a test can hand the view model a different one. The `Uri` comes
 * from the system picker, so this app reads exactly the one file it was pointed at and still
 * asks for no storage permission.
 *
 * A backup of a lifetime of movements is still a small file — this app's rows are short and
 * there is one owner — so it is read whole rather than streamed. If that ever stops being true,
 * the thing to change is the parser, not this.
 */
@Singleton
open class DocumentReader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        open suspend fun read(uri: Uri): String =
            withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)) {
                    "The picker returned a document that cannot be read"
                }.use { stream -> stream.readBytes().decodeToString() }
            }
    }
