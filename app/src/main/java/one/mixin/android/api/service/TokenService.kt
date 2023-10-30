package one.mixin.android.api.service

import one.mixin.android.api.MixinResponse
import one.mixin.android.api.request.TransferRequest
import one.mixin.android.api.request.WithdrawalRequest
import one.mixin.android.api.response.PaymentResponse
import one.mixin.android.ui.wallet.BaseTransactionsFragment.Companion.LIMIT
import one.mixin.android.vo.AssetPrecision
import one.mixin.android.vo.Chain
import one.mixin.android.vo.safe.SafeSnapshot
import one.mixin.android.vo.Ticker
import one.mixin.android.vo.safe.Token
import one.mixin.android.vo.TopAsset
import one.mixin.android.vo.safe.PendingDeposit
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TokenService {

    @GET("safe/assets")
    fun assets(): Call<MixinResponse<List<Token>>>

    @GET("safe/assets")
    suspend fun fetchAllAssetSuspend(): MixinResponse<List<Token>>

    @GET("safe/assets/{id}")
    suspend fun getAssetByIdSuspend(@Path("id") id: String): MixinResponse<Token>

    @GET("safe/assets/{id}")
    suspend fun getAssetPrecisionById(@Path("id") id: String): MixinResponse<AssetPrecision>

    @GET("safe/snapshots")
    suspend fun getSnapshotsByAssetId(
        @Query("asset") id: String,
        @Query("offset") offset: String? = null,
        @Query("limit") limit: Int = LIMIT,
    ): MixinResponse<List<SafeSnapshot>>

    @GET("safe/snapshots")
    suspend fun getAllSnapshots(
        @Query("offset") offset: String? = null,
        @Query("limit") limit: Int = LIMIT,
        @Query("opponent") opponent: String? = null,
    ): MixinResponse<List<SafeSnapshot>>

    @GET("safe/snapshots")
    suspend fun getSnapshots(
        @Query("asset") assetId: String,
        @Query("offset") offset: String? = null,
        @Query("limit") limit: Int = LIMIT,
        @Query("opponent") opponent: String? = null,
        @Query("destination") destination: String? = null,
        @Query("tag") tag: String? = null,
    ): MixinResponse<List<SafeSnapshot>>


    @POST("payments")
    suspend fun paySuspend(@Body request: TransferRequest): MixinResponse<PaymentResponse>

    @POST("withdrawals")
    suspend fun withdrawals(@Body request: WithdrawalRequest): MixinResponse<SafeSnapshot>

    @GET("safe/snapshots/{id}")
    suspend fun getSnapshotById(@Path("id") id: String): MixinResponse<SafeSnapshot>

    @GET("safe/external/transactions")
    suspend fun pendingDeposits(
        @Query("asset") asset: String,
        @Query("destination") key: String? = null,
        @Query("tag") tag: String? = null,
    ): MixinResponse<List<PendingDeposit>>

    @GET("network/assets/search/{query}")
    suspend fun queryAssets(@Path("query") query: String): MixinResponse<List<Token>>

    @GET("network/assets/top")
    fun topAssets(@Query("kind") kind: String = "NORMAL"): Call<MixinResponse<List<TopAsset>>>

    @GET("safe/snapshots/trace/{id}")
    suspend fun getTrace(@Path("id") traceId: String): MixinResponse<SafeSnapshot>

    @GET("network/ticker")
    suspend fun ticker(
        @Query("asset") assetId: String,
        @Query("offset") offset: String? = null,
    ): MixinResponse<Ticker>

    @GET("network/chains")
    suspend fun getChains(): MixinResponse<List<Chain>>

    @GET("network/chains/{id}")
    suspend fun getChainById(@Path("id") id: String): MixinResponse<Chain>
}