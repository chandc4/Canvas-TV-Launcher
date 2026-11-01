package com.canvas.tv.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MovieAdapter(
    private var movies: List<File>,
    private val onMovieClick: (File) -> Unit,
    private val onMovieLongClick: (File) -> Unit
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    class MovieViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.movieFileName)
        val size: TextView = view.findViewById(R.id.movieFileSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie_card, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val file = movies[position]
        holder.name.text = file.name
        holder.size.text = String.format("%.1f MB", file.length() / (1024.0 * 1024.0))

        holder.itemView.setOnClickListener {
            onMovieClick(file)
        }

        holder.itemView.setOnLongClickListener {
            onMovieLongClick(file)
            true
        }

        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }
        }
    }

    override fun getItemCount(): Int = movies.size

    fun updateMovies(newMovies: List<File>) {
        this.movies = newMovies
        notifyDataSetChanged()
    }
}
