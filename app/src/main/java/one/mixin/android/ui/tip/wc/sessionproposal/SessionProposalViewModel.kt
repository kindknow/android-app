package one.mixin.android.ui.tip.wc.sessionproposal

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import one.mixin.android.tip.wc.WalletConnect
import one.mixin.android.tip.wc.WalletConnectTIP
import one.mixin.android.tip.wc.WalletConnectV1
import one.mixin.android.tip.wc.WalletConnectV2
import javax.inject.Inject

@HiltViewModel
class SessionProposalViewModel @Inject internal constructor() : ViewModel() {

    fun rejectSession(version: WalletConnect.Version) {
        when (version) {
            WalletConnect.Version.V1 -> { WalletConnectV1.rejectSession() }
            WalletConnect.Version.V2 -> { WalletConnectV2.rejectSession() }
            WalletConnect.Version.TIP -> {}
        }
    }

    fun getSessionProposalUI(version: WalletConnect.Version): SessionProposalUI? {
        when (version) {
            WalletConnect.Version.V1 -> {
                val session = WalletConnectV1.currentSession ?: return null
                val peer = session.remotePeerMeta
                return SessionProposalUI(
                    peer = PeerUI(
                        uri = peer.url.toUri().host ?: "",
                        name = peer.name,
                        desc = peer.description ?: "",
                        icon = peer.icons.firstOrNull().toString(),
                    ),
                    chain = WalletConnectV1.chain,
                )
            }
            WalletConnect.Version.V2 -> {
                val sessionProposal = WalletConnectV2.getSessionProposals().lastOrNull()
                return if (sessionProposal != null) {
                    SessionProposalUI(
                        peer = PeerUI(
                            icon = sessionProposal.icons.firstOrNull().toString(),
                            name = sessionProposal.name,
                            desc = sessionProposal.description,
                            uri = sessionProposal.url.toUri().host ?: "",
                        ),
                        chain = WalletConnectV2.chain,
                    )
                } else {
                    null
                }
            }
            WalletConnect.Version.TIP -> {
                return WalletConnectTIP.getSessionProposalUI()
            }
        }
    }

    fun getTargetSwitchNetworks() = WalletConnectV1.targetNetwork
}