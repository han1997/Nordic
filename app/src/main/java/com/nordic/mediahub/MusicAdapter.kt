package com.nordic.mediahub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicAdapter(private val tracks: List<Track>) : RecyclerView.Adapter<MusicAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val trackNumber: TextView = view.findViewById(R.id.track_number)
        val songTitle: TextView = view.findViewById(R.id.song_title)
        val songArtist: TextView = view.findViewById(R.id.song_artist)
        val songDuration: TextView = view.findViewById(R.id.song_duration)
        val btnMore: ImageButton = view.findViewById(R.id.btn_more)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        holder.trackNumber.text = (position + 1).toString()
        holder.songTitle.text = track.title
        holder.songArtist.text = track.artist
        holder.songDuration.text = track.duration
    }

    override fun getItemCount() = tracks.size
}
