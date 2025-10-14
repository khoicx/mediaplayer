package com.khoicx.mediaplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val songs: List<String>,
    private val onItemClick: (Int) -> Unit // Lambda để xử lý sự kiện click
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    // Tạo ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.song_item, parent, false)
        return SongViewHolder(view)
    }

    // Gắn dữ liệu vào ViewHolder
    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.songNameTextView.text = songs[position]
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
    }

    // Trả về số lượng mục
    override fun getItemCount() = songs.size

    // Lớp ViewHolder để giữ các view của một mục
    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val songNameTextView: TextView = itemView.findViewById(R.id.textViewSongName)
    }
}
