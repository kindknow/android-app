package one.mixin.android.ui.sticker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import one.mixin.android.R
import one.mixin.android.databinding.ItemAlbumBinding
import one.mixin.android.databinding.ItemStickerBinding
import one.mixin.android.extension.dp
import one.mixin.android.extension.loadSticker
import one.mixin.android.vo.Sticker
import one.mixin.android.vo.StickerAlbum
import one.mixin.android.widget.RLottieImageView

class AlbumAdapter : ListAdapter<StickerAlbum, AlbumHolder>(StickerAlbum.DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        AlbumHolder(ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: AlbumHolder, position: Int) {
        getItem(position)?.let { album -> holder.bind(album) }
    }
}

class AlbumHolder(val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(album: StickerAlbum) {
        val ctx = binding.root.context
        binding.apply {
            tileTv.text = album.name
            actionTv.text = ctx.getString(R.string.sticker_store_add)
            actionTv.setOnClickListener { }

            val adapter = StickerAdapter()
            stickerRv.apply {
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
                this.adapter = adapter
            }
        }
    }
}

private class StickerAdapter : ListAdapter<Sticker, StickerViewHolder>(Sticker.DIFF_CALLBACK) {
    private val size: Int = 72.dp

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        val params = holder.itemView.layoutParams
        params.width = size
        params.height = size
        holder.itemView.layoutParams = params
        val item = (holder.itemView as ViewGroup).getChildAt(0) as RLottieImageView
        item.updateLayoutParams<ViewGroup.LayoutParams> {
            width = size
            height = size
        }
        getItem(position)?.let { s ->
            item.loadSticker(s.assetUrl, s.assetType)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        StickerViewHolder(ItemStickerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
}

private class StickerViewHolder(val binding: ItemStickerBinding) : RecyclerView.ViewHolder(binding.root)
