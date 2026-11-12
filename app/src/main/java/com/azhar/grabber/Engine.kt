package com.azhar.grabber

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

/** One-time setup of the yt-dlp + ffmpeg engine (first run unpacks python, takes a few seconds). */
object Engine {
    @Volatile
    private var ready = false

    @Synchronized
    fun init(context: Context) {
        if (ready) return
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
        ready = true
    }
}
