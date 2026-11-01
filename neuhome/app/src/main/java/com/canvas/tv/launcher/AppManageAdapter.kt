package com.canvas.tv.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppManageAdapter(
    private var apps: List<AppModel>,
    private var hidden: Set<String>,
    private val onHiddenChanged: (AppModel, Boolean) -> Unit,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit
) : RecyclerView.Adapter<AppManageAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val title: TextView = view.findViewById(R.id.appTitle)
        val hideSwitch: androidx.appcompat.widget.SwitchCompat = view.findViewById(R.id.hideSwitch)
        val up: android.widget.Button = view.findViewById(R.id.btnMoveUp)
        val down: android.widget.Button = view.findViewById(R.id.btnMoveDown)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_manage, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.title.text = app.label
        if (app.icon != null) holder.icon.setImageDrawable(app.icon) else if (app.banner != null) holder.icon.setImageDrawable(app.banner)

        holder.hideSwitch.setOnCheckedChangeListener(null)
        holder.hideSwitch.isChecked = app.packageName !in hidden
        holder.hideSwitch.setOnCheckedChangeListener { _, isChecked ->
            onHiddenChanged(app, !isChecked)
        }

        holder.up.setOnClickListener { onMoveUp(holder.bindingAdapterPosition) }
        holder.down.setOnClickListener { onMoveDown(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = apps.size

    fun update(apps: List<AppModel>, hidden: Set<String>) {
        this.apps = apps
        this.hidden = hidden
        notifyDataSetChanged()
    }

    fun currentApps(): List<AppModel> = apps
}