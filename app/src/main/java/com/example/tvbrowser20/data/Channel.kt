package com.example.tvbrowser20.data

data class Channel(
    val id: String,
    val channelNum: String,   // e.g. "CCTV-1"
    val name: String,         // e.g. "综合频道"
    val sub: String = "",     // e.g. "新闻·综合"
    val url: String = "",
    val logoUrl: String? = null,
    val group: String? = null
)
