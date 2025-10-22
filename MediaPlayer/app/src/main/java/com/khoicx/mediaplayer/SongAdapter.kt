package com.khoicx.mediaplayer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var songs: List<String> = emptyList()
    private var playingIndex: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.song_item, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.songNameTextView.text = songs[position]
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }

        if (position == playingIndex) {
            holder.itemView.setBackgroundColor(Color.LTGRAY)
            holder.songNameTextView.setTextColor(Color.BLACK)
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            holder.songNameTextView.setTextColor(Color.WHITE)
        }
    }

    override fun getItemCount() = songs.size

    fun submitList(newSongs: List<String>) {
        songs = newSongs
        notifyDataSetChanged() // For a production app, DiffUtil would be more efficient here
    }

    fun updatePlayingIndex(newIndex: Int) {
        val oldIndex = playingIndex
        playingIndex = newIndex
        if (oldIndex > -1) {
            notifyItemChanged(oldIndex)
        }
        if (newIndex > -1) {
            notifyItemChanged(newIndex)
        }
    }

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songNameTextView: TextView = itemView.findViewById(R.id.textViewSongName)
    }
}
