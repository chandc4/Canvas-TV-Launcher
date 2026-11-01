package com.canvas.tv.launcher

import android.graphics.drawable.Drawable

data class AppModel(
    val label: String,
    val packageName: String,
    val activityName: String,
    val banner: Drawable?,
    val icon: Drawable?
)
