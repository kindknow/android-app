package one.mixin.android.vo.safe

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
class SafeDeposit(
    @SerializedName("deposit_hash")
    @SerialName("deposit_hash")
    val depositHash: String,
) : Parcelable