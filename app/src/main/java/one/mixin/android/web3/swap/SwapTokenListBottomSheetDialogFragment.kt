package one.mixin.android.web3.swap

import android.annotation.SuppressLint
import android.app.Dialog
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.jakewharton.rxbinding3.widget.textChanges
import com.uber.autodispose.autoDispose
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.response.web3.SwapToken
import one.mixin.android.databinding.FragmentAssetListBottomSheetBinding
import one.mixin.android.extension.appCompatActionBarHeight
import one.mixin.android.extension.containsIgnoreCase
import one.mixin.android.extension.getParcelableArrayListCompat
import one.mixin.android.extension.hideKeyboard
import one.mixin.android.extension.navTo
import one.mixin.android.extension.statusBarHeight
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.MixinBottomSheetDialogFragment
import one.mixin.android.ui.home.web3.swap.SwapViewModel
import one.mixin.android.util.viewBinding
import one.mixin.android.web3.receive.Web3AddressFragment
import one.mixin.android.widget.BottomSheet
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class SwapTokenListBottomSheetDialogFragment : MixinBottomSheetDialogFragment() {
    companion object {
        const val ARGS_TOKENS = "args_tokens"
        const val TAG = "SwapTokenListBottomSheetDialogFragment"

        fun newInstance(tokens: ArrayList<SwapToken>) =
            SwapTokenListBottomSheetDialogFragment().withArgs {
                putParcelableArrayList(ARGS_TOKENS, tokens)
            }
    }

    private val binding by viewBinding(FragmentAssetListBottomSheetBinding::inflate)
    private val swapViewModel by viewModels<SwapViewModel>()

    private val tokens by lazy {
        requireArguments().getParcelableArrayListCompat(ARGS_TOKENS, SwapToken::class.java)
    }

    private val adapter by lazy {
        SwapTokenAdapter()
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)
        contentView = binding.root
        binding.ph.updateLayoutParams<ViewGroup.LayoutParams> {
            height = requireContext().statusBarHeight() + requireContext().appCompatActionBarHeight()
        }
        (dialog as BottomSheet).apply {
            setCustomView(contentView)
        }

        binding.apply {
            assetRv.adapter = adapter
            adapter.tokens = tokens!!
            searchEt.et.setHint(R.string.search_swap_token)
            closeIb.setOnClickListener {
                searchEt.hideKeyboard()
                dismiss()
            }
            if (tokens.isNullOrEmpty()) {
                rvVa.displayedChild = 2
            } else {
                rvVa.displayedChild = 0
            }
            depositTv.setText(R.string.Receive)
            depositTv.setOnClickListener {
                navTo(Web3AddressFragment(), Web3AddressFragment.TAG)
                dismiss()
            }
            searchEt.et.textChanges().debounce(500L, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .autoDispose(stopScope)
                .subscribe({
                    searchJob?.cancel()
                    searchJob = filter(it.toString())
                }, {})
        }
    }

    private var searchJob: Job? = null

    private fun filter(s: String) =
        lifecycleScope.launch {
            if (s.isBlank()) {
                adapter.tokens = tokens!!
                if (tokens.isNullOrEmpty()) {
                    binding.rvVa.displayedChild = 2
                } else {
                    binding.rvVa.displayedChild = 0
                }
                return@launch
            }
            val assetList =
                tokens?.filter {
                    it.name.containsIgnoreCase(s) || it.symbol.containsIgnoreCase(s)
                }?.toMutableList() ?: mutableListOf()

            val total = search(s, assetList)

            adapter.tokens = ArrayList(total)
            if (!isAdded) {
                return@launch
            }
            if (adapter.itemCount == 0) {
                binding.rvVa.displayedChild = 1
            } else {
                binding.rvVa.displayedChild = 0
            }
        }

    private suspend fun search(
        s: String,
        localTokens: MutableList<SwapToken>,
    ): List<SwapToken> {
        if (s.isBlank()) return localTokens

        handleMixinResponse(
            invokeNetwork = { swapViewModel.searchTokens(s) },
            successBlock = { resp ->
                return@handleMixinResponse resp.data
            },
        )?.let { remoteList ->
            localTokens.addAll(
                remoteList.filter { ra ->
                    !localTokens.any { a -> a.address.equals(ra.address, true) }
                },
            )
        }
        return localTokens
    }

    fun setOnClickListener(onClickListener: (SwapToken, Boolean) -> Unit) {
        this.adapter.setOnClickListener(onClickListener)
    }
}