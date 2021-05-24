package one.mixin.android.vo

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize
import java.io.Serializable

@Parcelize
@Entity(tableName = "transcripts", primaryKeys = ["transcript_id", "message_id"])
class Transcript(
    @SerializedName("transcript_id")
    @ColumnInfo(name = "transcript_id")
    val transcriptId: String,
    @SerializedName("message_id")
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @SerializedName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String?,
    @SerializedName("user_full_name")
    @ColumnInfo(name = "user_full_name")
    val userFullName: String,
    @SerializedName("category")
    @ColumnInfo(name = "category")
    override val type: String,
    @SerializedName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @SerializedName("content")
    @ColumnInfo(name = "content")
    val content: String?,
    @SerializedName("media_url")
    @ColumnInfo(name = "media_url")
    var mediaUrl: String? = null,
    @SerializedName("media_name")
    @ColumnInfo(name = "media_name")
    val mediaName: String? = null,
    @SerializedName("media_size")
    @ColumnInfo(name = "media_size")
    val mediaSize: Long? = null,
    @SerializedName("media_width")
    @ColumnInfo(name = "media_width")
    val mediaWidth: Int? = null,
    @SerializedName("media_height")
    @ColumnInfo(name = "media_height")
    val mediaHeight: Int? = null,
    @SerializedName("media_mime_type")
    @ColumnInfo(name = "media_mime_type")
    val mediaMimeType: String? = null,
    @SerializedName("media_duration")
    @ColumnInfo(name = "media_duration")
    val mediaDuration: String? = null,
    @Expose(deserialize = false, serialize = false)
    @ColumnInfo(name = "media_status")
    var mediaStatus: String? = null,
    @SerializedName("media_waveform")
    @ColumnInfo(name = "media_waveform")
    val mediaWaveform: ByteArray? = null,
    @SerializedName("thumb_image")
    @ColumnInfo(name = "thumb_image")
    val thumbImage: String? = null,
    @SerializedName("thumb_url")
    @ColumnInfo(name = "thumb_url")
    val thumbUrl: String? = null,
    @SerializedName("media_key")
    @ColumnInfo(name = "media_key")
    val mediaKey: ByteArray? = null,
    @SerializedName("media_digest")
    @ColumnInfo(name = "media_digest")
    val mediaDigest: ByteArray? = null,
    @SerializedName("media_created_at")
    @ColumnInfo(name = "media_created_at")
    val mediaCreatedAt: String? = null,
    @SerializedName("sticker_id")
    @ColumnInfo(name = "sticker_id")
    val stickerId: String? = null,
    @SerializedName("shared_user_id")
    @ColumnInfo(name = "shared_user_id")
    val sharedUserId: String? = null,
    @SerializedName("mentions")
    @ColumnInfo(name = "mentions")
    val mentions: String? = null,
    @SerializedName("quote_id")
    @ColumnInfo(name = "quote_id")
    val quoteId: String? = null,
    @SerializedName("quote_content")
    @ColumnInfo(name = "quote_content")
    var quoteContent: String? = null,
    @SerializedName("caption")
    @ColumnInfo(name = "caption")
    val caption: String? = null
) : ICategory, Serializable, Parcelable

fun Transcript.copy(tid: String): Transcript {
    return Transcript(
        tid,
        messageId,
        userId,
        userFullName,
        type,
        createdAt,
        content,
        mediaUrl,
        mediaName,
        mediaSize,
        mediaWidth,
        mediaHeight,
        mediaMimeType,
        mediaDuration,
        mediaStatus,
        mediaWaveform,
        thumbImage,
        thumbUrl,
        mediaKey,
        mediaDigest,
        mediaCreatedAt,
        stickerId,
        sharedUserId,
        mentions,
        quoteId,
        quoteContent,
        caption
    )
}