package com.canvas.tv.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.io.File

class WallpaperThumbAdapter(
    private var wallpapers: List<File>,
    private val onWallpaperClick: (File) -> Unit,
    private val onWallpaperLongClick: (File) -> Unit
) : RecyclerView.Adapter<WallpaperThumbAdapter.ThumbViewHolder>() {

    class ThumbViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.wallpaperThumb)
        val hint: TextView = view.findViewById(R.id.thumbActionHint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wallpaper_thumb, parent, false)
        return ThumbViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThumbViewHolder, position: Int) {
        val file = wallpapers[position]
        holder.thumb.load(file) {
            crossfade(true)
            size(160, 90)
        }

        holder.itemView.setOnClickListener {
            onWallpaperClick(file)
        }

        holder.itemView.setOnLongClickListener {
            onWallpaperLongClick(file)
            true
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                holder.hint.visibility = View.VISIBLE
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                holder.hint.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = wallpapers.size

    fun updateWallpapers(newWallpapers: List<File>) {
        this.wallpapers = newWallpapers
        notifyDataSetChanged()
    }
}
