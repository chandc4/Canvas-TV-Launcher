package com.canvas.tv.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MinimalAppAdapter(
    private var apps: List<AppModel>,
    private val onAppFocused: (AppModel) -> Unit,
    private val onAppClick: (AppModel) -> Unit
) : RecyclerView.Adapter<MinimalAppAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val title: TextView = view.findViewById(R.id.appTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_minimal, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.title.text = app.label

        if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon)
        } else if (app.banner != null) {
            holder.icon.setImageDrawable(app.banner)
        }

        holder.itemView.setOnClickListener {
            onAppClick(app)
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start()
                onAppFocused(app)
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppModel>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}
