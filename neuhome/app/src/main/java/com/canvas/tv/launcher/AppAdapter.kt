package com.canvas.tv.launcher

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private var apps: List<AppModel>,
    private val onAppClick: (AppModel) -> Unit,
    private val onAppLongClick: (AppModel) -> Unit = {},
    private val onAppFocused: (AppModel) -> Unit = {}
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val title: TextView = view.findViewById(R.id.appTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.title.text = app.label
        
        if (app.banner != null) {
            holder.icon.setImageDrawable(app.banner)
        } else if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon)
        }

        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app)
            true
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Keep focus indication via border glow only (no scale expansion)
                view.animate().translationZ(8f).setDuration(160).start()
                onAppFocused(app)
            } else {
                view.animate().translationZ(0f).setDuration(160).start()
            }
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppModel>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}
