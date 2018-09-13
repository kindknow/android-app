package one.mixin.android.ui.setting

import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.uber.autodispose.kotlin.autoDisposable
import kotlinx.android.synthetic.main.fragment_authentications.*
import kotlinx.android.synthetic.main.item_auth.view.*
import kotlinx.android.synthetic.main.view_title.view.*
import one.mixin.android.R
import one.mixin.android.extension.loadImage
import one.mixin.android.ui.common.BaseFragment
import one.mixin.android.vo.App
import javax.inject.Inject

class AuthenticationsFragment : BaseFragment() {
    companion object {
        const val TAG = "AuthenticationsFragment"

        fun newInstance() = AuthenticationsFragment()
    }

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private val settingViewModel: SettingViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(SettingViewModel::class.java)
    }

    private var list: MutableList<App>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_authentications, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        title_view.left_ib.setOnClickListener { activity?.onBackPressed() }
        val adapter = AuthenticationAdapter(object : OnAppClick {
            override fun onClick(app: App, position: Int) {
                settingViewModel.deauthApp(app.appId).autoDisposable(scopeProvider).subscribe({}, {})
                list?.removeAt(position)
                auth_rv.adapter?.notifyItemRemoved(position)
            }
        })
        settingViewModel.authorizations().autoDisposable(scopeProvider).subscribe({ list ->
            if (list.isSuccess) {
                this.list = list.data?.map {
                    it.app
                }?.run {
                    MutableList(this.size) {
                        this[it]
                    }
                }
                adapter.submitList(this.list)
            }

        }, {})
        auth_rv.adapter = adapter
    }

    class AuthenticationAdapter(private val onAppClick: OnAppClick) : ListAdapter<App, ItemHolder>(App.DIFF_CALLBACK) {
        override fun onBindViewHolder(itemHolder: ItemHolder, pos: Int) {
            itemHolder.bindTo(getItem(pos), onAppClick)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemHolder =
            ItemHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_auth, parent, false))
    }

    interface OnAppClick {
        fun onClick(app: App, position: Int)
    }

    class ItemHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindTo(app: App, onAppClick: OnAppClick) {
            itemView.avatar.loadImage(app.icon_url)
            itemView.name_tv.text = app.name
            itemView.deauthorize.setOnClickListener {
                onAppClick.onClick(app, adapterPosition)
            }
        }
    }
}