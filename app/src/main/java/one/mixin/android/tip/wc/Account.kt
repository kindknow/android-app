package one.mixin.android.tip.wc

private const val ISS_DID_PREFIX = "did:pkh:"

fun Pair<Chain, String>.toIssuer(): String = "$ISS_DID_PREFIX${first.chainId}:$second"
