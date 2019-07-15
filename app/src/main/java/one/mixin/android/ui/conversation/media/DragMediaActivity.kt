package one.mixin.android.ui.conversation.media

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.TextureView
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.demo.systemuidemo.SystemUIManager
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.STATE_BUFFERING
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.shizhefei.view.largeimage.LargeImageView
import com.shizhefei.view.largeimage.factory.FileBitmapDecoderFactory
import com.tbruyelle.rxpermissions2.RxPermissions
import com.uber.autodispose.autoDisposable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_drag_media.*
import kotlinx.android.synthetic.main.item_video_layout.view.*
import kotlinx.android.synthetic.main.view_drag_bottom.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.R
import one.mixin.android.extension.belowOreo
import one.mixin.android.extension.copyFromInputStream
import one.mixin.android.extension.createGifTemp
import one.mixin.android.extension.createImageTemp
import one.mixin.android.extension.createPngTemp
import one.mixin.android.extension.decodeQR
import one.mixin.android.extension.displayRatio
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.fadeIn
import one.mixin.android.extension.fadeOut
import one.mixin.android.extension.formatMillis
import one.mixin.android.extension.getFilePath
import one.mixin.android.extension.getPublicPicturePath
import one.mixin.android.extension.getUriForFile
import one.mixin.android.extension.isGooglePlayServicesAvailable
import one.mixin.android.extension.loadGif
import one.mixin.android.extension.loadImage
import one.mixin.android.extension.loadVideo
import one.mixin.android.extension.openPermissionSetting
import one.mixin.android.extension.realSize
import one.mixin.android.extension.screenWidth
import one.mixin.android.extension.statusBarHeight
import one.mixin.android.extension.supportsPie
import one.mixin.android.extension.toast
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.ui.PipVideoView
import one.mixin.android.ui.common.BaseActivity
import one.mixin.android.ui.common.QrScanBottomSheetDialogFragment
import one.mixin.android.ui.url.openUrl
import one.mixin.android.util.AnimationProperties
import one.mixin.android.util.video.MixinPlayer
import one.mixin.android.vo.MessageCategory
import one.mixin.android.vo.MessageItem
import one.mixin.android.vo.isLive
import one.mixin.android.widget.BottomSheet
import one.mixin.android.widget.PhotoView.DismissFrameLayout
import one.mixin.android.widget.PhotoView.PhotoView
import one.mixin.android.widget.PlayView
import one.mixin.android.widget.PlayView.Companion.STATUS_BUFFERING
import one.mixin.android.widget.PlayView.Companion.STATUS_IDLE
import one.mixin.android.widget.PlayView.Companion.STATUS_LOADING
import one.mixin.android.widget.PlayView.Companion.STATUS_PAUSING
import one.mixin.android.widget.PlayView.Companion.STATUS_PLAYING
import one.mixin.android.widget.gallery.MimeType
import org.jetbrains.anko.backgroundDrawable
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.Random
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.min

class DragMediaActivity : BaseActivity(), DismissFrameLayout.OnDismissListener {
    private lateinit var colorDrawable: ColorDrawable
    private val conversationId by lazy {
        intent.getStringExtra(CONVERSATION_ID)
    }
    private val messageId by lazy {
        intent.getStringExtra(MESSAGE_ID)
    }

    private var index: Int = 0
    private var lastPos: Int = -1
    private lateinit var pagerAdapter: MediaAdapter
    private var disposable: Disposable? = null

    @Inject
    lateinit var conversationRepository: ConversationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        postponeEnterTransition()
        if (pipVideoView.shown) {
            pipVideoView.close()
        }
        super.onCreate(savedInstanceState)
        VideoPlayer.player().setOnVideoPlayerListener(videoListener)
        belowOreo {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_drag_media)
        colorDrawable = ColorDrawable(Color.BLACK)
        view_pager.backgroundDrawable = colorDrawable

        val model = ViewModelProviders.of(this).get(DragMediaViewModel::class.java)
        model.viewModelScope.launch {
            val list = conversationRepository.getMediaMessages(conversationId).filter { item ->
                if (item.type == MessageCategory.PLAIN_LIVE.name) {
                    true
                } else {
                    File(item.mediaUrl?.toUri()?.getFilePath()).exists()
                }
            }.reversed()

            index = list.indexOfFirst { item -> messageId == item.messageId }
            pagerAdapter = MediaAdapter(list, this@DragMediaActivity)
            view_pager.adapter = pagerAdapter
            if (index != -1) {
                view_pager.currentItem = index
                lastPos = index
                play(index)
            } else {
                view_pager.currentItem = 0
                lastPos = 0
                this@DragMediaActivity.finish()
            }
        }

        view_pager.addOnPageChangeListener(pageListener)
        window.decorView.systemUiVisibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                SYSTEM_UI_FLAG_FULLSCREEN or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        supportsPie {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        SystemUIManager.setSystemUiColor(window, Color.BLACK)
        SystemUIManager.lightUI(window, false)
    }

    override fun onPause() {
        super.onPause()
        if (!pipVideoView.shown) {
            pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        VideoPlayer.player().setOnVideoPlayerListener(null)
        if (!pipVideoView.shown) {
            VideoPlayer.destroy()
        }
    }

    private fun showBottom() {
        val builder = BottomSheet.Builder(this)
        val view = View.inflate(ContextThemeWrapper(this, R.style.Custom), R.layout.view_drag_bottom, null)
        builder.setCustomView(view)
        val bottomSheet = builder.create()
        view.save.setOnClickListener {
            RxPermissions(this)
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .autoDisposable(stopScope)
                .subscribe({ granted ->
                    if (granted) {
                        doAsync {
                            pagerAdapter.list?.let { list ->
                                val item = list[view_pager.currentItem]
                                val file = File(item.mediaUrl?.toUri()?.getFilePath())
                                val outFile = when {
                                    item.mediaMimeType.equals(MimeType.GIF.toString(), true) -> this@DragMediaActivity.getPublicPicturePath().createGifTemp(false)
                                    item.mediaMimeType.equals(MimeType.PNG.toString()) -> this@DragMediaActivity.getPublicPicturePath().createPngTemp(false)
                                    else -> this@DragMediaActivity.getPublicPicturePath().createImageTemp(noMedia = false)
                                }
                                outFile.copyFromInputStream(FileInputStream(file))
                                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile)))
                                uiThread { toast(R.string.save_success) }
                            }
                        }
                    } else {
                        openPermissionSetting()
                    }
                }, {
                    toast(R.string.save_failure)
                })
            bottomSheet.dismiss()
        }
        view.decode.setOnClickListener {
            findViewPagerChildByTag { viewGroup ->
                decodeQRCode(viewGroup)
            }
            bottomSheet.dismiss()
        }
        view.cancel.setOnClickListener { bottomSheet.dismiss() }

        bottomSheet.show()
    }

    private fun decodeQRCode(viewGroup: ViewGroup) {
        val imageView = viewGroup.getChildAt(0) as ImageView
        if (imageView.drawable is BitmapDrawable) {
            if (isGooglePlayServicesAvailable()) {
                val image = FirebaseVisionImage.fromBitmap((imageView.drawable as BitmapDrawable).bitmap)
                val detector = FirebaseVision.getInstance().visionBarcodeDetector
                detector.detectInImage(image)
                    .addOnSuccessListener { barcodes ->
                        val url = barcodes.firstOrNull()?.rawValue
                        if (url != null) {
                            openUrl(url, supportFragmentManager) {
                                QrScanBottomSheetDialogFragment.newInstance(url)
                                    .showNow(supportFragmentManager, QrScanBottomSheetDialogFragment.TAG)
                            }
                        } else {
                            toast(R.string.can_not_recognize)
                        }
                    }
                    .addOnFailureListener {
                        toast(R.string.can_not_recognize)
                    }
            } else {
                lifecycleScope.launch {
                    val url = withContext(Dispatchers.IO) {
                        (imageView.drawable as BitmapDrawable).bitmap.decodeQR()
                    }
                    if (url != null) {
                        openUrl(url, supportFragmentManager) {
                            QrScanBottomSheetDialogFragment.newInstance(url)
                                .showNow(supportFragmentManager, QrScanBottomSheetDialogFragment.TAG)
                        }
                    } else {
                        toast(R.string.can_not_recognize)
                    }
                }
            }
        } else {
            toast(R.string.can_not_recognize)
        }
    }

    private fun shareVideo() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            val url = pagerAdapter.list?.get(view_pager.currentItem)?.mediaUrl
            var uri = Uri.parse(url)
            if (ContentResolver.SCHEME_FILE == uri.scheme) {
                uri = getUriForFile(File(uri.getFilePath(this@DragMediaActivity)))
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            type = "video/*"
        }
        startActivity(Intent.createChooser(sendIntent, "Share video to.."))
    }

    inner class MediaAdapter(
        val list: List<MessageItem>?,
        private val onDismissListener: DismissFrameLayout.OnDismissListener
    ) : PagerAdapter(), TextureView.SurfaceTextureListener {

        fun getItem(position: Int): MessageItem = list!![position]

        override fun getCount(): Int = list?.size ?: 0

        override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val messageItem = getItem(position)
            val innerView = if (messageItem.type == MessageCategory.SIGNAL_IMAGE.name ||
                messageItem.type == MessageCategory.PLAIN_IMAGE.name) {
                if (messageItem.mediaHeight!! / messageItem.mediaWidth!!.toFloat() > displayRatio() * 1.5f) {
                    createLargeImageView(container, position, messageItem)
                } else {
                    createPhotoView(container, position, messageItem)
                }
            } else {
                createVideoView(container, position, messageItem)
            }
            val layout = DismissFrameLayout(container.context)
            layout.setDismissListener(onDismissListener)
            layout.layoutParams = ViewPager.LayoutParams()
            layout.addView(innerView)
            layout.tag = "$PREFIX$position"
            container.addView(layout)
            return layout
        }

        private fun createVideoView(container: ViewGroup, position: Int, messageItem: MessageItem): View {
            val view = View.inflate(container.context, R.layout.item_video_layout, null)
            view.close_iv.setOnClickListener { finishAfterTransition() }
            view.pip_iv.setOnClickListener {
                switchToPip()
            }
            (view.share_iv.layoutParams as FrameLayout.LayoutParams).marginEnd = baseContext.dpToPx(44f)
            view.share_iv.setOnClickListener { shareVideo() }
            view.close_iv.post {
                val statusBarHeight = statusBarHeight().toFloat()
                view.close_iv.translationY = statusBarHeight
                view.share_iv.translationY = statusBarHeight
                view.pip_iv.translationY = statusBarHeight
            }
            view.video_texture.surfaceTextureListener = this
            val ratio = messageItem.mediaWidth!!.toFloat() / messageItem.mediaHeight!!.toFloat()
            setSize(ratio, view)
            if (messageItem.isLive()) {
                view.preview_iv.loadImage(messageItem.thumbUrl!!, messageItem.thumbImage)
            } else {
                view.preview_iv.loadVideo(messageItem.mediaUrl!!)
                view.seek_bar.progress = 0
                view.duration_tv.text = 0L.formatMillis()
                view.remain_tv.text = messageItem.mediaDuration?.toLong()?.formatMillis()
            }

            view.preview_iv.visibility = VISIBLE
            view.tag = messageItem.type == MessageCategory.PLAIN_LIVE.name

            if (position == index) {
                ViewCompat.setTransitionName(view.preview_iv, "transition")
                setStartPostTransition(view.preview_iv)
            }

            if (position != view_pager.currentItem) {
                view.play_view.visibility = VISIBLE
            }

            view.play_view.setOnClickListener {
                when (view.play_view.status) {
                    STATUS_IDLE -> {
                        setPreviewIv(false, view_pager.currentItem)
                        play(view_pager.currentItem)
                    }
                    STATUS_LOADING, STATUS_PLAYING, STATUS_BUFFERING -> {
                        pause()
                    }
                    STATUS_PAUSING -> {
                        start()
                    }
                }
            }
            view.setOnClickListener {
                if (view.close_iv.isVisible) {
                    fadeOut(view, messageItem.type == MessageCategory.PLAIN_LIVE.name)
                } else {
                    fadeIn(view, messageItem.type == MessageCategory.PLAIN_LIVE.name)
                }
            }

            view.video_texture.setOnClickListener {
                if (view.close_iv.isVisible) {
                    fadeOut(view, messageItem.type == MessageCategory.PLAIN_LIVE.name)
                } else {
                    fadeIn(view, messageItem.type == MessageCategory.PLAIN_LIVE.name)
                }
            }

            var isPlaying = false
            view.seek_bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isPlaying = VideoPlayer.player().isPlaying()
                    VideoPlayer.player().pause()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (isPlaying) {
                        VideoPlayer.player().start()
                    }
                }

                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        VideoPlayer.player().seekTo(progress * VideoPlayer.player().duration() / 200)
                    }
                }
            })

            return view
        }

        private fun setSize(rotio: Float, view: View) {
            val w = applicationContext.realSize().x
            val h = applicationContext.realSize().y
            val previewParams = view.preview_iv.layoutParams
            val ratioParams = view.video_aspect_ratio.layoutParams
            if (rotio > 1f) {
                val scaleH = (w / rotio).toInt()
                previewParams.width = w
                ratioParams.width = w
                previewParams.height = scaleH
                ratioParams.height = scaleH
            } else {
                val scaleW = (h * rotio).toInt()
                previewParams.width = scaleW
                ratioParams.width = scaleW
                previewParams.height = h
                ratioParams.height = h
            }
            view.preview_iv.layoutParams = previewParams
            view.video_aspect_ratio.layoutParams = ratioParams
        }

        private fun createLargeImageView(container: ViewGroup, position: Int, messageItem: MessageItem): LargeImageView {
            val imageView = LargeImageView(container.context)
            imageView.setImage(FileBitmapDecoderFactory(File(messageItem.mediaUrl?.getFilePath())))
            if (messageItem.mediaWidth!! < screenWidth()) {
                imageView.scale = (screenWidth().toFloat() / messageItem.mediaWidth)
            }
            if (position == index) {
                ViewCompat.setTransitionName(imageView, "transition")
                setStartPostTransition(imageView)
            }
            imageView.setOnClickListener {
                finishAfterTransition()
            }
            imageView.setOnLongClickListener {
                showBottom()
                return@setOnLongClickListener true
            }
            return imageView
        }

        private fun createPhotoView(container: ViewGroup, position: Int, messageItem: MessageItem): PhotoView {
            val imageView = PhotoView(container.context)
            imageView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            if (messageItem.mediaMimeType.equals(MimeType.GIF.toString(), true)) {
                imageView.loadGif(messageItem.mediaUrl, object : RequestListener<GifDrawable?> {
                    override fun onResourceReady(
                        resource: GifDrawable?,
                        model: Any?,
                        target: Target<GifDrawable?>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        if (position == index) {
                            ViewCompat.setTransitionName(imageView, "transition")
                            setStartPostTransition(imageView)
                        }
                        return false
                    }

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<GifDrawable?>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
            } else {
                imageView.loadImage(messageItem.mediaUrl, object : RequestListener<Drawable?> {
                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable?>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        if (position == index) {
                            ViewCompat.setTransitionName(imageView, "transition")
                            setStartPostTransition(imageView)
                        }
                        return false
                    }

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable?>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
            }
            imageView.setOnClickListener {
                finishAfterTransition()
            }
            imageView.setOnLongClickListener {
                showBottom()
                return@setOnLongClickListener true
            }
            return imageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            if (obj is View) {
                obj.tag?.let {
                    if (it is Disposable && !it.isDisposed) {
                        it.dispose()
                    }
                }
            }
            container.removeView(obj as View)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean = false

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
            setTextureView()
        }
    }

    private fun fadeIn(view: View, live: Boolean, withoutPlay: Boolean = false) {
        if (live) {
            view.pip_iv.fadeIn()
            view.close_iv.fadeIn()
            view.play_view.fadeIn()
        } else {
            if (!withoutPlay) {
                view.play_view.fadeIn()
            }
            view.controller.fadeIn()
            view.close_iv.fadeIn()
            view.pip_iv.fadeIn()
            view.share_iv.fadeIn()
        }
    }

    private fun fadeOut(view: View, live: Boolean, withoutPlay: Boolean = false) {
        if (live) {
            view.pip_iv.fadeOut()
            view.close_iv.fadeOut()
            view.play_view.fadeOut()
        } else {
            if (!withoutPlay) {
                view.play_view.fadeOut()
            } else {
                view.play_view.fadeIn()
            }
            view.controller.fadeOut()
            view.close_iv.fadeOut()
            view.pip_iv.fadeOut()
            view.share_iv.fadeOut()
        }
    }

    private fun setTextureView() {
        findViewPagerChildByTag {
            val parentView = it.getChildAt(0)
            if (parentView is FrameLayout) {
                VideoPlayer.player().setVideoTextureView(parentView.video_texture)
            }
        }
    }

    private fun setPlayViewStatus(status: Int, pos: Int = lastPos) {
        findViewPagerChildByTag(pos) {
            val parentView = it.getChildAt(0)
            if (parentView is FrameLayout) {
                (parentView.getChildAt(2) as PlayView).status = status
            }
        }
    }

    private fun setPreviewIv(visible: Boolean, pos: Int = lastPos) {
        findViewPagerChildByTag(pos) {
            val parentView = it.getChildAt(0)
            if (parentView is FrameLayout) {
                parentView.preview_iv.visibility = if (visible) VISIBLE else INVISIBLE
            }
        }
    }

    private fun handleLast() {
        findViewPagerChildByTag(lastPos) {
            val parentView = it.getChildAt(0)
            if (parentView is FrameLayout) {
                fadeOut(parentView, parentView.tag as Boolean, true)
                parentView.preview_iv.visibility = VISIBLE
            }
        }
    }

    private fun setStartPostTransition(sharedView: View) {
        sharedView.doOnPreDraw { startPostponedEnterTransition() }
    }

    override fun onDismissProgress(progress: Float) {
        colorDrawable.alpha = min(ALPHA_MAX, ((1 - progress) * ALPHA_MAX).toInt())
    }

    override fun onDismiss() {
        finishAfterTransition()
    }

    override fun finishAfterTransition() {
        window.decorView.systemUiVisibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        if (view_pager.currentItem == index) {
            super.finishAfterTransition()
        } else {
            finish()
        }
    }

    override fun finish() {
        ValueAnimator.ofInt(0, 100)
            .setDuration(400)
            .apply {
                addUpdateListener {
                    (it.animatedValue as Int).apply {
                        val v = view_pager.findViewWithTag<DismissFrameLayout>("$PREFIX${view_pager.currentItem}")
                            ?: return@addUpdateListener
                        v.translationY = (realSize().y * this / 100).toFloat()
                        colorDrawable.alpha = ALPHA_MAX * (100 - this) / 100
                        if (it.animatedValue == 100) {
                            super.finish()
                            overridePendingTransition(R.anim.no_transition, R.anim.no_transition)
                        }
                    }
                }
            }
            .start()
    }

    override fun onCancel() {
        colorDrawable.alpha = ALPHA_MAX
    }

    private inline fun findViewPagerChildByTag(pos: Int = view_pager.currentItem, action: (v: ViewGroup) -> Unit) {
        val v = view_pager.findViewWithTag<DismissFrameLayout>("$PREFIX$pos")
        if (v != null) {
            action(v as ViewGroup)
        }
    }

    private fun start() {
        view_pager.post {
            findViewPagerChildByTag { viewGroup ->
                val parentView = viewGroup.getChildAt(0)
                if (parentView is FrameLayout) {
                    fadeOut(parentView, parentView.tag as Boolean)
                    (parentView.getChildAt(2) as PlayView).status = STATUS_PLAYING
                    disposable = Observable.interval(0, 100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .autoDisposable(stopScope)
                        .subscribe {
                            if (VideoPlayer.player().duration() != 0) {
                                parentView.seek_bar.progress = (VideoPlayer.player().getCurrentPos() * 200 /
                                    VideoPlayer.player().duration()).toInt()
                                parentView.duration_tv.text = VideoPlayer.player().getCurrentPos().formatMillis()
                                if (parentView.remain_tv.text.isEmpty()) { // from google photo
                                    parentView.remain_tv.text = VideoPlayer.player().duration().toLong().formatMillis()
                                }
                            }
                        }
                }
            }
        }

        VideoPlayer.player().start()
    }

    private fun pause() {
        setPlayViewStatus(STATUS_PAUSING)
        disposable?.dispose()
        VideoPlayer.player().pause()
    }

    private fun stop() {
        setPlayViewStatus(STATUS_IDLE)
        handleLast()
        disposable?.dispose()
        VideoPlayer.player().stop()
    }

    private inline fun load(pos: Int, action: () -> Unit = {}) {
        val messageItem = pagerAdapter.getItem(pos)
        if (messageItem.type == MessageCategory.SIGNAL_VIDEO.name ||
            messageItem.type == MessageCategory.PLAIN_VIDEO.name ||
            messageItem.type == MessageCategory.PLAIN_LIVE.name) {
            messageItem.mediaUrl?.let {
                VideoPlayer.player().loadVideo(it)
            }
            setTextureView()
            action()
        }
    }

    private fun play(pos: Int) = load(pos) { start() }

    private val videoListener = object : MixinPlayer.VideoPlayerListenerWrapper() {
        override fun onRenderedFirstFrame() {
            findViewPagerChildByTag {
                val parentView = it.getChildAt(0)
                if (parentView is FrameLayout) {
                    parentView.preview_iv.visibility = INVISIBLE
                }
            }
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            if (VideoPlayer.player().isPlaying() && isLoading && VideoPlayer.player().player.playbackState == STATE_BUFFERING) {
                setPlayViewStatus(STATE_BUFFERING)
            }
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                stop()
            }
        }

        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            var nWidth = width
            var nHeight = height
            if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                nWidth = height
                nHeight = width
            }
            val ratio = (if (nHeight == 0) 1 else nWidth * pixelWidthHeightRatio / nHeight) as Float
            findViewPagerChildByTag {
                val parentView = it.getChildAt(0)
                if (parentView is FrameLayout) {
                    parentView.video_aspect_ratio.setAspectRatio(ratio, unappliedRotationDegrees)
                }
            }
        }
    }

    private val pageListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        }

        override fun onPageSelected(position: Int) {
            if (lastPos == -1 || lastPos == position) return

            stop()
            lastPos = position
        }
    }
    private var pipAnimationInProgress = false
    private fun switchToPip() {
        if (!checkInlinePermissions() || pipAnimationInProgress) {
            return
        }
        pipAnimationInProgress = true
        findViewPagerChildByTag {
            val windowView = it.getChildAt(0)
            val rect = PipVideoView.getPipRect(windowView.video_aspect_ratio.aspectRatio)
            val with = windowView.width
            val scale = rect.width / with
            val animatorSet = AnimatorSet()
            val position = IntArray(2)
            windowView.video_aspect_ratio.getLocationOnScreen(position)
            val changedTextureView = pipVideoView.show(
                this, windowView.video_aspect_ratio.aspectRatio,
                windowView.video_aspect_ratio.videoRotation,
                conversationId, messageId)

            animatorSet.playTogether(
                ObjectAnimator.ofInt(colorDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, 0),
                ObjectAnimator.ofFloat(windowView.video_texture, View.SCALE_X, scale),
                ObjectAnimator.ofFloat(windowView.video_texture, View.SCALE_Y, scale),
                ObjectAnimator.ofFloat(windowView.video_aspect_ratio, View.TRANSLATION_X, rect.x - windowView.video_aspect_ratio.x
                    - this.realSize().x * (1f - scale) / 2),
                ObjectAnimator.ofFloat(windowView.video_aspect_ratio, View.TRANSLATION_Y, rect.y - windowView.video_aspect_ratio.y
                    + this.statusBarHeight() - (windowView.video_aspect_ratio.height - rect.height) / 2))
            animatorSet.interpolator = DecelerateInterpolator()
            animatorSet.duration = 250
            animatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    pipAnimationInProgress = false
                    VideoPlayer.player().setVideoTextureView(changedTextureView)
                    dismiss()
                }
            })
            animatorSet.start()

        }
    }

    private fun dismiss() {
        container.visibility = INVISIBLE
        overridePendingTransition(0, 0)
        super.finish()
    }

    private val pipVideoView by lazy {
        PipVideoView.getInstance()
    }

    private fun checkInlinePermissions(): Boolean {
        if (Settings.canDrawOverlays(this)) {
            return true
        } else {
            this.let { activity ->
                AlertDialog.Builder(activity)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.live_permission)
                    .setPositiveButton(R.string.live_setting) { _, _ ->
                        try {
                            activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName)))
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }.show()
            }
        }
        return false
    }

    companion object {
        private const val MESSAGE_ID = "id"
        private const val CONVERSATION_ID = "conversation_id"
        private const val ALPHA_MAX = 0xFF
        private const val PREFIX = "media"

        fun show(activity: Activity, imageView: View, conversationId: String, messageId: String) {
            val intent = Intent(activity, DragMediaActivity::class.java).apply {
                putExtra(CONVERSATION_ID, conversationId)
                putExtra(MESSAGE_ID, messageId)
            }
            activity.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(activity, imageView,
                "transition").toBundle())
        }

        fun show(context: Context, conversationId: String, messageId: String) {
            val intent = Intent(context, DragMediaActivity::class.java).apply {
                addFlags(FLAG_ACTIVITY_NEW_TASK)
                putExtra(CONVERSATION_ID, conversationId)
                putExtra(MESSAGE_ID, messageId)
            }
            context.startActivity(intent)
        }
    }
}