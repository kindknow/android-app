package one.mixin.android.tip.wc

sealed class Method(val name: String) {
    object ETHSign : Method("eth_sign")
    object ETHPersonalSign : Method("personal_sign")
    object ETHSignTypedData : Method("eth_signTypedData")
    object ETHSignTypedDataV4 : Method("eth_signTypedData_v4")
    object ETHSignTransaction : Method("eth_signTransaction")
    object ETHSendTransaction : Method("eth_sendTransaction")
}