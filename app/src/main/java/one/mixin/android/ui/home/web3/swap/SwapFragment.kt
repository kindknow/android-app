package one.mixin.android.ui.home.web3.swap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import one.mixin.android.Constants.Account.PREF_SWAP_LAST_SELECTED_PAIR
import one.mixin.android.Constants.Account.PREF_SWAP_SLIPPAGE
import one.mixin.android.Constants.AssetId.USDT_ASSET_ID
import one.mixin.android.Constants.RouteConfig.ROUTE_BOT_USER_ID
import one.mixin.android.R
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.web3.SwapRequest
import one.mixin.android.api.response.Web3Token
import one.mixin.android.api.response.solanaNativeTokenAssetKey
import one.mixin.android.api.response.web3.QuoteResult
import one.mixin.android.api.response.web3.SwapResponse
import one.mixin.android.api.response.web3.SwapToken
import one.mixin.android.api.response.web3.Swappable
import one.mixin.android.api.response.wrappedSolTokenAssetKey
import one.mixin.android.compose.theme.MixinAppTheme
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.defaultSharedPreferences
import one.mixin.android.extension.forEachWithIndex
import one.mixin.android.extension.getParcelableArrayListCompat
import one.mixin.android.extension.isNightMode
import one.mixin.android.extension.navTo
import one.mixin.android.extension.openMarket
import one.mixin.android.extension.putInt
import one.mixin.android.extension.putString
import one.mixin.android.extension.safeNavigateUp
import one.mixin.android.extension.withArgs
import one.mixin.android.session.Session
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.ui.home.web3.TransactionStateFragment
import one.mixin.android.ui.home.web3.showBrowserBottomSheetDialogFragment
import one.mixin.android.ui.wallet.AssetListBottomSheetDialogFragment
import one.mixin.android.ui.wallet.AssetListBottomSheetDialogFragment.Companion.TYPE_FROM_SEND
import one.mixin.android.ui.wallet.SwapTransferBottomSheetDialogFragment
import one.mixin.android.util.ErrorHandler
import one.mixin.android.vo.safe.TokenItem
import one.mixin.android.web3.ChainType
import one.mixin.android.web3.js.JsSignMessage
import one.mixin.android.web3.js.JsSigner
import one.mixin.android.web3.js.SolanaTxSource
import one.mixin.android.web3.receive.Web3TokenListBottomSheetDialogFragment
import one.mixin.android.web3.swap.SwapTokenListBottomSheetDialogFragment
import timber.log.Timber

@AndroidEntryPoint
class SwapFragment : BaseFragment() {
    companion object {
        const val TAG = "SwapFragment"
        const val ARGS_WEB3_TOKENS = "args_web3_tokens"
        const val ARGS_TOKEN_ITEMS = "args_token_items"
        const val ARGS_INPUT = "args_input"
        const val ARGS_OUTPUT = "args_output"
        const val ARGS_AMOUNT = "args_amount"

        const val MaxSlippage = 5000
        const val DangerousSlippage = 500
        const val MinSlippage = 10
        const val DefaultSlippage = 100

        const val maxLeftAmount = 0.01

        inline fun <reified T : Swappable> newInstance(
            tokens: List<T>? = null,
            input: String? = null,
            output: String? = null,
            amount: String? = null,
        ): SwapFragment =
            SwapFragment().withArgs {
                when (T::class) {
                    Web3Token::class -> {
                        putParcelableArrayList(ARGS_WEB3_TOKENS, arrayListOf<T>().apply {
                            if (tokens != null) {
                                addAll(tokens)
                            }
                        })
                    }

                    TokenItem::class -> {
                        putParcelableArrayList(ARGS_TOKEN_ITEMS, arrayListOf<T>().apply { tokens?.let { addAll(it) } })
                    }
                }
                input?.let { putString(ARGS_INPUT, it) }
                output?.let { putString(ARGS_OUTPUT, it) }
                amount?.let { putString(ARGS_AMOUNT, it) }
            }
    }

    enum class SwapDestination {
        Swap,
    }

    private var swapTokens: List<SwapToken> by mutableStateOf(emptyList())
    private var tokenItems: List<TokenItem>? = null
    private val web3tokens: List<Web3Token>? by lazy {
        requireArguments().getParcelableArrayListCompat(ARGS_WEB3_TOKENS, Web3Token::class.java)
    }
    private var fromToken: SwapToken? by mutableStateOf(null)
    private var toToken: SwapToken? by mutableStateOf(null)

    private var initialAmount: String? = null
    private var slippage: Int by mutableIntStateOf(DefaultSlippage)

    private val swapViewModel by viewModels<SwapViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        slippage = defaultSharedPreferences.getInt(PREF_SWAP_SLIPPAGE, DefaultSlippage)
        if (slippage > DefaultSlippage) {
            slippage = DefaultSlippage
            defaultSharedPreferences.putInt(PREF_SWAP_SLIPPAGE, DefaultSlippage)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        initAmount()
        lifecycleScope.launch {
            initFromTo()
            refreshTokens()
        }
        return ComposeView(inflater.context).apply {
            setContent {
                MixinAppTheme(
                    darkTheme = context.isNightMode(),
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = SwapDestination.Swap.name,
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(300),
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(300),
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(300),
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(300),
                            )
                        },
                    ) {
                        composable(SwapDestination.Swap.name) {
                            SwapPage(
                                from = fromToken,
                                to = toToken,
                                initialAmount = initialAmount,
                                slippageBps = slippage,
                                onSelectToken = { type ->
                                    selectCallback(swapTokens, type)
                                },
                                onSwap = { quote, from, to, amount ->
                                    lifecycleScope.launch {
                                        handleSwap(quote, from, to, amount)
                                    }
                                },
                                source = getSource(),
                                onShowSlippage = {
                                    SwapSlippageBottomSheetDialogFragment.newInstance(slippage)
                                        .setOnSlippage { bps ->
                                            slippage = bps
                                            defaultSharedPreferences.putInt(PREF_SWAP_SLIPPAGE, bps)
                                        }
                                        .showNow(parentFragmentManager, SwapSlippageBottomSheetDialogFragment.TAG)
                                },
                                pop = {
                                    navigateUp(navController)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private val selectCallback = fun(
        list: List<SwapToken>,
        type: SelectTokenType,
    ) {
        if (type == SelectTokenType.From) {
            if (inMixin()) {
                AssetListBottomSheetDialogFragment.newInstance(TYPE_FROM_SEND, ArrayList(list.map { t -> t.assetId }))
                    .setOnAssetClick { t ->
                        val token = t.toSwapToken()
                        if (token == toToken) {
                            toToken = fromToken
                        }
                        fromToken = token
                        fromToken?.let { from ->
                            toToken?.let { to ->
                                defaultSharedPreferences.putString(PREF_SWAP_LAST_SELECTED_PAIR, "${from.getUnique()} ${to.getUnique()}")
                            }
                        }
                    }.setOnDepositClick {
                        parentFragmentManager.popBackStackImmediate()
                    }
                    .showNow(parentFragmentManager, AssetListBottomSheetDialogFragment.TAG)
            } else {
                val data = ArrayList(web3tokens ?: emptyList())
                Web3TokenListBottomSheetDialogFragment.newInstance(
                    data
                ).apply {
                    setOnClickListener { t ->
                        val token = t.toSwapToken()
                        if (token == toToken) {
                            toToken = fromToken
                        }
                        fromToken = token
                        dismissNow()
                    }
                }.show(parentFragmentManager, Web3TokenListBottomSheetDialogFragment.TAG)
            }
        } else {
            SwapTokenListBottomSheetDialogFragment.newInstance(
                ArrayList(
                    list.run {
                        this
                    },
                ),
            ).apply {
                if (list.isEmpty()) {
                    setLoading(true)
                }
                setOnClickListener { token, alert ->
                    if (alert) {
                        SwapTokenBottomSheetDialogFragment.newInstance(token).showNow(parentFragmentManager, SwapTokenBottomSheetDialogFragment.TAG)
                        return@setOnClickListener
                    }
                    if (token == fromToken) {
                        fromToken = toToken
                    }
                    toToken = token
                    if (inMixin()) {
                        fromToken?.let { from ->
                            toToken?.let { to ->
                                defaultSharedPreferences.putString(PREF_SWAP_LAST_SELECTED_PAIR, "${from.getUnique()} ${to.getUnique()}")
                            }
                        }
                    }
                    dismissNow()
                }
            }.show(parentFragmentManager, SwapTokenListBottomSheetDialogFragment.TAG)
        }
    }

    private suspend fun handleSwap(quote: QuoteResult, form: SwapToken, to: SwapToken, amount: String) {
        val inputMint = form.getUnique()
        val outputMint = to.getUnique()

        val resp = handleMixinResponse(
            invokeNetwork = {
                swapViewModel.web3Swap(
                    SwapRequest(
                        if (inMixin()) Session.getAccountId()!! else JsSigner.solanaAddress,
                        inputMint,
                        if (inMixin()) 0 else amount.toLong(),
                        if (inMixin()) amount else "0",
                        outputMint,
                        slippage,
                        getSource(),
                        quote.payload,
                        quote.jupiterQuoteResponse
                    )
                )
            },
            successBlock = { it.data },
            exceptionBlock = { t ->
                Timber.e(t)
                false
            },
            failureBlock = { r ->
                Timber.e(r.errorDescription)
                false
            }
        )
        if (resp == null) return
        if (inMixin()) {
            swapViewModel.checkAndSyncTokens(listOfNotNull(form.assetId, to.assetId))
            openSwapTransfer(resp)
        } else {
            val signMessage = JsSignMessage(0, JsSignMessage.TYPE_RAW_TRANSACTION, data = resp.tx, solanaTxSource = SolanaTxSource.InnerSwap)
            JsSigner.useSolana()
            showBrowserBottomSheetDialogFragment(requireActivity(), signMessage) { hash, serializedTx ->
                lifecycleScope.launch {
                    val txStateFragment = TransactionStateFragment.newInstance(serializedTx, to.symbol).apply {
                        setCloseAction {
                            findNavController().navigateUp()
                            parentFragmentManager.popBackStackImmediate()
                        }
                    }
                    navTo(txStateFragment, TransactionStateFragment.TAG)
                }
            }
        }
    }

    private suspend fun openSwapTransfer(swapResult: SwapResponse) {
        val inputToken = tokenItems?.find { it.assetId == swapResult.quote.inputMint } ?: swapViewModel.findToken(swapResult.quote.inputMint) ?: throw IllegalStateException(getString(R.string.Data_error))
        val outToken = tokenItems?.find { it.assetId == swapResult.quote.outputMint } ?: swapViewModel.findToken(swapResult.quote.outputMint) ?: throw IllegalStateException(getString(R.string.Data_error))
        SwapTransferBottomSheetDialogFragment.newInstance(swapResult, inputToken, outToken).apply {
            setOnDone {
                // Todo
                // clearInputAndRefreshInMixinFromToToken()
            }
        }.showNow(parentFragmentManager, SwapTransferBottomSheetDialogFragment.TAG)
    }

    private suspend fun initFromTo() {
        tokenItems = requireArguments().getParcelableArrayListCompat(ARGS_TOKEN_ITEMS, TokenItem::class.java)
        var swappable = web3tokens ?: tokenItems
        if (web3tokens?.isEmpty() == true) { // Only supplement local data for local assets
            swappable = emptyList()
        } else if (swappable.isNullOrEmpty()) {
            swappable = swapViewModel.allAssetItems()
            tokenItems = swappable
        }
        swappable.let { tokens ->
            val input = requireArguments().getString(ARGS_INPUT)
            val output = requireArguments().getString(ARGS_OUTPUT)
            val lastSelectedPair = defaultSharedPreferences.getString(PREF_SWAP_LAST_SELECTED_PAIR, null)?.split(" ")
            val lastFrom = lastSelectedPair?.getOrNull(0)
            val lastTo = lastSelectedPair?.getOrNull(1)
            if (tokens.isNotEmpty()) {
                fromToken = (input?.let { tokens.firstOrNull { t -> t.getUnique() == input } } ?: tokens.firstOrNull { t -> t.getUnique() == lastFrom })?.toSwapToken() ?: tokens[0].toSwapToken()
                toToken = if (input != null && output == null) {
                    tokens.firstOrNull { t -> t.getUnique() == USDT_ASSET_ID }?.toSwapToken() ?: tokens.firstOrNull { t -> t.getUnique() == lastTo }?.toSwapToken() ?: tokens[1].toSwapToken()
                } else {
                    (output?.let { tokens.firstOrNull { t -> t.getUnique() == output } } ?: tokens.firstOrNull { t -> t.getUnique() == lastTo })?.toSwapToken() ?: tokens[1].toSwapToken()
                }
                if (toToken?.getUnique() == fromToken?.getUnique()) {
                    toToken = tokens.firstOrNull { t -> t.getUnique() != fromToken?.getUnique() }?.toSwapToken()
                }
            }
        }
    }

    private suspend fun refreshTokens() {
        handleMixinResponse(
            invokeNetwork = { swapViewModel.web3Tokens(getSource()) },
            successBlock = { resp ->
                resp.data
            },
            failureBlock = { r ->
                if (r.errorCode == 401) {
                    swapViewModel.getBotPublicKey(ROUTE_BOT_USER_ID, true)
                    refreshTokens()
                } else if (r.errorCode == ErrorHandler.OLD_VERSION) {
                    alertDialogBuilder()
                        .setTitle(R.string.Update_Mixin)
                        .setMessage(getString(R.string.update_mixin_description, requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName))
                        .setNegativeButton(R.string.Later) { dialog, _ ->
                            dialog.dismiss()
                            activity?.onBackPressedDispatcher?.onBackPressed()
                        }.setPositiveButton(R.string.Update) { dialog, _ ->
                            requireContext().openMarket()
                            dialog.dismiss()
                            activity?.onBackPressedDispatcher?.onBackPressed()
                        }.setCancelable(false)
                        .create().show()
                }
                return@handleMixinResponse true
            },
        )?.let {
            if (!inMixin()) {
                swapTokens = it.map { token ->
                    val t = web3tokens?.firstOrNull { web3Token ->
                        web3Token.assetKey == token.address || (token.address == wrappedSolTokenAssetKey && web3Token.assetKey == solanaNativeTokenAssetKey)
                    } ?: return@map token
                    token.balance = t.balance
                    token
                }
                if (fromToken == null) {
                    fromToken = swapTokens.firstOrNull { t -> fromToken == t } ?: swapTokens[0]
                }
                toToken = swapTokens.firstOrNull { s -> s.address != fromToken?.address }
            } else {
                swapTokens = it.map { token ->
                    val t = tokenItems?.firstOrNull { tokenItem ->
                        tokenItem.assetId == token.assetId
                    } ?: return@map token
                    token.balance = t.balance
                    token.price = t.priceUsd
                    token
                }
                if (fromToken == null) {
                    fromToken = swapTokens.firstOrNull { t -> fromToken == t } ?: swapTokens[0]
                    toToken = swapTokens.getOrNull(1)
                } else {
                    val found = swapTokens.firstOrNull { s -> s.assetId == fromToken?.assetId }
                    if (toToken != null) {
                        val toFound = swapTokens.firstOrNull { s -> s.assetId == toToken?.assetId }
                        if (toFound == null) {
                            toToken = swapTokens.getOrNull(1)
                        }
                    } else {
                        toToken = swapTokens.getOrNull(1)
                    }
                }
            }
            if (swapTokens.isNotEmpty()) {
                (parentFragmentManager.findFragmentByTag(SwapTokenListBottomSheetDialogFragment.TAG) as? SwapTokenListBottomSheetDialogFragment)?.setLoading(false, swapTokens)
            }
            if (fromToken != null && toToken != null) {
                refreshTokensPrice(listOf(fromToken!!, toToken!!))
            }
        }
    }

    private suspend fun refreshTokensPrice(tokens: List<SwapToken>): List<SwapToken> {
        if (inMixin()) {
            val newTokens = swapViewModel.syncAndFindTokens(tokens.map { it.assetId })
            if (newTokens.isEmpty()) {
                return tokens
            }
            tokens.forEachWithIndex { _, token ->
                newTokens.forEach { t ->
                    if (t.assetId == token.assetId) {
                        token.price = t.priceUsd
                    }
                }
            }
        } else {
            val web3Tokens = swapViewModel.web3Tokens(chain = ChainType.solana.name, address = tokens.map { it.address })
            if (web3Tokens.isEmpty()) {
                return tokens
            }
            tokens.forEachIndexed { _, token ->
                web3Tokens.forEach { t ->
                    if (t.assetKey.equals(token.address, true)) {
                        token.price = t.price
                    }
                }
            }
        }
        return tokens
    }

    private fun initAmount() {
        initialAmount = arguments?.getString(ARGS_AMOUNT)
    }

    private fun inMixin(): Boolean = web3tokens == null
    private fun getSource(): String = if (inMixin()) "mixin" else ""

    private fun navigateUp(navController: NavHostController) {
        if (!navController.safeNavigateUp()) {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
    }
}
