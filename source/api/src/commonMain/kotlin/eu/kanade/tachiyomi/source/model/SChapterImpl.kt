package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject
import kotlin.jvm.Transient

class SChapterImpl : SChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

    override var date_upload: Long = 0

    @Transient
    override var memo: JsonObject = JsonObject(emptyMap())
}
