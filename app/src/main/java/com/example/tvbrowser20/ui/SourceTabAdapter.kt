package com.example.tvbrowser20.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tvbrowser20.R
import com.example.tvbrowser20.data.Source

class SourceTabAdapter(
    private val onTabClick: (index: Int) -> Unit
) : RecyclerView.Adapter<SourceTabAdapter.ViewHolder>() {

    private var sources: List<Source> = emptyList()
    private var activeIndex: Int = 0

    fun setSources(list: List<Source>) {
        sources = list
        notifyDataSetChanged()
    }

    fun setActive(index: Int) {
        val old = activeIndex
        activeIndex = index
        if (old != index) {
            notifyItemChanged(old)
            notifyItemChanged(index)
        }
    }

    override fun getItemCount() = sources.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_source_tab, parent, false) as TextView
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val src      = sources[position]
        val isActive = position == activeIndex

        holder.text.text = src.label

        if (isActive) {
            holder.text.setBackgroundResource(R.drawable.bg_source_tab_active)
            holder.text.setTextColor(0xFFFFFFFF.toInt())
        } else {
            holder.text.setBackgroundResource(R.drawable.bg_source_tab_normal)
            holder.text.setTextColor(0x8CFFFFFF.toInt())
        }

        holder.text.setOnClickListener { onTabClick(position) }
    }

    class ViewHolder(val text: TextView) : RecyclerView.ViewHolder(text)
}
