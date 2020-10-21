package one.mixin.android.ui.web

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_web.*
import kotlinx.android.synthetic.main.view_six.*
import one.mixin.android.R
import one.mixin.android.extension.alertDialogBuilder
import one.mixin.android.extension.dp
import one.mixin.android.extension.round
import one.mixin.android.ui.common.BaseActivity
import one.mixin.android.vo.App
import one.mixin.android.vo.AppCardData

@AndroidEntryPoint
class WebActivity : BaseActivity() {

    companion object {
        fun show(context: Context) {
            context.startActivity(
                Intent(context, WebActivity::class.java).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            )
        }

        fun show(
            context: Context,
            url: String,
            conversationId: String?,
            app: App? = null,
            appCard: AppCardData? = null
        ) {
            context.startActivity(
                Intent(context, WebActivity::class.java).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    putExtras(
                        Bundle().apply {
                            putString(WebFragment.URL, url)
                            putString(WebFragment.CONVERSATION_ID, conversationId)
                            putParcelable(WebFragment.ARGS_APP, app)
                            putParcelable(WebFragment.ARGS_APP_CARD, appCard)
                        }
                    )
                }
            )
        }
    }

    override fun getNightThemeId(): Int = R.style.AppTheme_Night_Transparent

    override fun getDefaultThemeId(): Int = R.style.AppTheme_Transparent

    private lateinit var layouts: List<FrameLayout>
    private lateinit var thumbs: List<ImageView>
    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.extras != null) {
            overridePendingTransition(R.anim.slide_in_bottom, 0)
        } else {
            overridePendingTransition(R.anim.fade_in, 0)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)
        container.setOnClickListener {
            finish()
        }
        layouts = listOf(
            thumbnail_layout_1,
            thumbnail_layout_2,
            thumbnail_layout_3,
            thumbnail_layout_4,
            thumbnail_layout_5,
            thumbnail_layout_6
        )
        thumbs = listOf(
            thumbnail_iv_1,
            thumbnail_iv_2,
            thumbnail_iv_3,
            thumbnail_iv_4,
            thumbnail_iv_5,
            thumbnail_iv_6
        )
        close_1.setOnClickListener {
            releaseClip(0)
            loadData()
        }
        close_2.setOnClickListener {
            releaseClip(1)
            loadData()
        }
        close_3.setOnClickListener {
            releaseClip(2)
            loadData()
        }
        close_4.setOnClickListener {
            releaseClip(3)
            loadData()
        }
        close_5.setOnClickListener {
            releaseClip(4)
            loadData()
        }
        close_6.setOnClickListener {
            releaseClip(5)
            loadData()
        }
        clear.setOnClickListener {
            alertDialogBuilder()
                .setMessage(getString(R.string.conversation_delete_tip))
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(R.string.confirm) { _, _ ->
                    releaseAll()
                    finish()
                }
                .show()
        }
        thumbnail_layout_1.round(8.dp)
        thumbnail_layout_2.round(8.dp)
        thumbnail_layout_3.round(8.dp)
        thumbnail_layout_4.round(8.dp)
        thumbnail_layout_5.round(8.dp)
        thumbnail_layout_6.round(8.dp)

        intent.extras?.let { extras ->
            isExpand = true
            supportFragmentManager.beginTransaction().add(
                R.id.container,
                WebFragment.newInstance(extras),
                WebFragment.TAG
            ).commit()
        }
        loadData()
    }

    private fun loadData() {
        repeat(6) { index ->
            if (index < clips.size) {
                layouts[index].visibility = View.VISIBLE
                thumbs[index].setImageBitmap(clips[index].thumb)
                layouts[index].setOnClickListener {
                    val extras = Bundle()
                    val clip = clips[index]
                    extras.putString(WebFragment.URL, clip.url)
                    extras.putParcelable(WebFragment.ARGS_APP, clip.app)
                    extras.putInt(WebFragment.ARGS_INDEX, index)
                    isExpand = true
                    supportFragmentManager.beginTransaction().add(
                        R.id.container,
                        WebFragment.newInstance(extras),
                        WebFragment.TAG
                    ).commit()
                }
            } else {
                layouts[index].visibility = View.INVISIBLE
            }
        }
    }

    private var isExpand = false

    override fun finish() {
        collapse()
        super.finish()
        if (isExpand) {
            overridePendingTransition(0, R.anim.slide_out_bottom)
        } else {
            overridePendingTransition(0, R.anim.fade_out)
        }
    }
}
