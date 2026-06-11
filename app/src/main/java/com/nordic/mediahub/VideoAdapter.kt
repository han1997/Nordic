package com.nordic.mediahub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VideoAdapter(private val videos: List<Video>) : RecyclerView.Adapter<VideoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val videoTitle: TextView = view.findViewById(R.id.video_title)
        val videoGenre: TextView = view.findViewById(R.id.video_genre)
        val videoDuration: TextView = view.findViewById(R.id.video_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.videoTitle.text = video.title
        holder.videoGenre.text = video.genre
        holder.videoDuration.text = video.duration
    }

    override fun getItemCount() = videos.size
}
