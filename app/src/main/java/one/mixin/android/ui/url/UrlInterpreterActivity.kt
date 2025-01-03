package one.mixin.android.ui.url

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import one.mixin.android.Constants
import one.mixin.android.MixinApplication
import one.mixin.android.R
import one.mixin.android.extension.checkUserOrApp
import one.mixin.android.extension.handleSchemeSend
import one.mixin.android.extension.toast
import one.mixin.android.session.Session
import one.mixin.android.tip.wc.WalletConnect
import one.mixin.android.ui.common.BaseActivity
import one.mixin.android.ui.conversation.link.LinkBottomSheetDialogFragment
import one.mixin.android.ui.device.ConfirmBottomFragment
import one.mixin.android.ui.home.MainActivity
import one.mixin.android.ui.oldwallet.OldTransferFragment
import one.mixin.android.ui.transfer.TransferActivity
import one.mixin.android.ui.web.WebActivity
import one.mixin.android.web3.convertWcLink
import timber.log.Timber

@AndroidEntryPoint
class UrlInterpreterActivity : BaseActivity() {
    companion object {
        private const val CODE = "codes"
        private const val PAY = "pay"
        private const val USER = "users"
        private const val TRANSFER = "transfer"
        private const val DEVICE = "device"
        private const val SEND = "send"
        private const val ADDRESS = "address"
        private const val APPS = "apps"
        private const val SNAPSHOTS = "snapshots"
        private const val CONVERSATIONS = "conversations"
        private const val DEVICE_TRANSFER = "device-transfer"
        private const val BUY = "buy"
        private const val TIP = "tip"
        private const val MULTISIGS = "multisigs"
        private const val SCHEME = "scheme"
        private const val MIXIN = "mixin.one"
        private const val SWAP = "swap"
        const val WC = "wc"

        fun show(
            context: Context,
            data: Uri,
        ) {
            Intent(context, UrlInterpreterActivity::class.java).apply {
                setData(data)
                context.startActivity(this)
            }
        }
    }

    override fun getDefaultThemeId(): Int {
        return R.style.AppTheme_Transparent
    }

    override fun getNightThemeId(): Int {
        return R.style.AppTheme_Night_Transparent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        if (data == null) {
            finish()
            return
        }
        if (Session.getAccount() == null) {
            toast(R.string.Not_logged_in)
            finish()
            return
        }

        if (data.toString().startsWith("https://", true)) {
            val bottomSheet = LinkBottomSheetDialogFragment.newInstance(data.toString(), LinkBottomSheetDialogFragment.FROM_EXTERNAL)
            bottomSheet.showNow(supportFragmentManager, LinkBottomSheetDialogFragment.TAG)
        } else if (data.toString().startsWith(Constants.Scheme.HTTPS_MIXIN_WC) || data.toString().startsWith(Constants.Scheme.MIXIN_WC) ||
            data.toString().startsWith(Constants.Scheme.WALLET_CONNECT_PREFIX)
        ) {
            val wcUri = convertWcLink(data.toString())
            if (wcUri != null && WalletConnect.isEnabled()) {
                if (MixinApplication.get().topActivity is WebActivity) {
                    WalletConnect.connect(wcUri.toString())
                } else {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            putExtra(MainActivity.WALLET_CONNECT, wcUri.toString())
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        },
                    )
                }
            } else {
                toast(R.string.Not_recognized)
            }
            finish()
        } else {
            interpretIntent(data)
        }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }

    private fun interpretIntent(uri: Uri) {
        when (uri.host) {
            USER, APPS -> uri.checkUserOrApp(this, supportFragmentManager, lifecycleScope)
            CODE, PAY, ADDRESS, SNAPSHOTS, CONVERSATIONS, TIP, SWAP -> {
                val bottomSheet = LinkBottomSheetDialogFragment.newInstance(uri.toString(), LinkBottomSheetDialogFragment.FROM_EXTERNAL)
                bottomSheet.showNow(supportFragmentManager, LinkBottomSheetDialogFragment.TAG)
            }
            TRANSFER -> {
                uri.lastPathSegment?.let { lastPathSegment ->
                    if (Session.getAccount()?.hasPin == true && Session.getTipPub() != null && Session.hasSafe()) {
                        OldTransferFragment.newInstance(lastPathSegment, supportSwitchAsset = true)
                            .showNow(supportFragmentManager, OldTransferFragment.TAG)
                    } else {
                        toast(R.string.transfer_without_pin)
                        finish()
                    }
                }
            }
            DEVICE -> {
                ConfirmBottomFragment.show(this, supportFragmentManager, uri.toString())
            }
            SEND -> {
                uri.handleSchemeSend(
                    this,
                    lifecycleScope,
                    supportFragmentManager,
                    afterShareText = { finish() },
                    onError = { err ->
                        Timber.e(IllegalStateException(err))
                    },
                )
            }
            DEVICE_TRANSFER -> {
                TransferActivity.parseUri(this, uri, { finish() }) { finish() }
            }
            BUY -> {
                MainActivity.showWallet(this, buy = true)
                finish()
            }
            MIXIN -> {
                val path = uri.pathSegments.first()
                if (path.equals(PAY, true) || path.equals(SCHEME, true) || path.equals(MULTISIGS, true) || path.equals(SWAP, true)) {
                    val bottomSheet = LinkBottomSheetDialogFragment.newInstance(uri.toString(), LinkBottomSheetDialogFragment.FROM_EXTERNAL)
                    bottomSheet.showNow(supportFragmentManager, LinkBottomSheetDialogFragment.TAG)
                } else {
                    toast(R.string.Invalid_Link)
                }
            }
            else -> {
                toast(R.string.Invalid_Link)
            }
        }
    }
}
