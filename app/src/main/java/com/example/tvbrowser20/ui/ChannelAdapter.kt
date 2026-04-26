package com.example.tvbrowser20.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tvbrowser20.R
import com.example.tvbrowser20.data.Channel

class ChannelAdapter(
    private val onClick: (index: Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

    private var channels: List<Channel> = emptyList()
    private var focusedIndex: Int = 0
    private var playingIndex: Int = -1

    // Colors cached once
    private val colorAccent        = 0xFFe60012.toInt()
    private val colorWhite         = 0xFFFFFFFF.toInt()
    private val colorNumDefault    = 0xFFe60012.toInt()
    private val colorNumFocused    = 0xBFFFFFFF.toInt()
    private val colorSubDefault    = 0x6BFFFFFF.toInt()
    private val colorSubFocused    = 0xA6FFFFFF.toInt()
    private val colorDotDefault    = 0x26FFFFFF.toInt()
    private val colorDotPlaying    = 0xFFe60012.toInt()
    private val colorDotFocused    = 0x73FFFFFF.toInt()
    private val colorDotFocPlaying = 0xFFFFFFFF.toInt()

    fun setChannels(list: List<Channel>) {
        channels = list
        notifyDataSetChanged()
    }

    fun setFocused(index: Int) {
        val old = focusedIndex
        focusedIndex = index
        if (old != index) {
            notifyItemChanged(old)
            notifyItemChanged(index)
        }
    }

    fun setPlaying(index: Int) {
        val old = playingIndex
        playingIndex = index
        if (old != index) {
            if (old >= 0) notifyItemChanged(old)
            if (index >= 0) notifyItemChanged(index)
        }
    }

    override fun getItemCount() = channels.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ch       = channels[position]
        val isFocused = position == focusedIndex
        val isPlaying = position == playingIndex

        // Background
        holder.itemView.setBackgroundColor(
            if (isFocused) colorAccent else Color.TRANSPARENT
        )

        // Left accent bar
        holder.accentBar.visibility = if (isFocused) View.VISIBLE else View.GONE

        // Channel num
        holder.chNum.text = ch.channelNum
        holder.chNum.setTextColor(if (isFocused) colorNumFocused else colorNumDefault)

        // Name
        holder.chName.text = ch.name

        // Sub
        holder.chSub.text = ch.sub
        holder.chSub.setTextColor(if (isFocused) colorSubFocused else colorSubDefault)

        // Status dot (circle)
        val dotColor = when {
            isFocused && isPlaying -> colorDotFocPlaying
            isFocused              -> colorDotFocused
            isPlaying              -> colorDotPlaying
            else                   -> colorDotDefault
        }
        (holder.chDot.background as? GradientDrawable)?.setColor(dotColor)
            ?: holder.chDot.setBackgroundColor(dotColor)

        holder.itemView.setOnClickListener { onClick(position) }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val accentBar: View    = itemView.findViewById(R.id.accentBar)
        val chNum: TextView    = itemView.findViewById(R.id.chNum)
        val chName: TextView   = itemView.findViewById(R.id.chName)
        val chSub: TextView    = itemView.findViewById(R.id.chSub)
        val chDot: View        = itemView.findViewById(R.id.chDot)

        init {
            // Ensure the dot is always a circle drawable
            val circle = GradientDrawable().apply {
                shape  = GradientDrawable.OVAL
                setColor(0x26FFFFFF)
            }
            chDot.background = circle
        }
    }
}
