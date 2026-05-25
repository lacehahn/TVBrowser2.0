package com.example.tvbrowser20.data

data class Source(
    val id: String,
    val label: String,
    val baseUrl: String,
    val channels: List<Channel>,
    /** Domains allowed for this source. Also contributes to the global whitelist. */
    val allowedDomains: List<String> = emptyList(),
    /** Key selecting which JS injection strategy to use. */
    val jsKey: String = JsKey.DEFAULT
)

object JsKey {
    const val DEFAULT = "default"
    const val CCTV    = "cctv"
    const val YIBA    = "yibababa"
    const val FAMELACK = "famelack"
    const val NONE    = "none"

    /** Sources that load one page and expose channel list + TVB_select via JS bridge. */
    fun usesWebChannelList(key: String): Boolean =
        key == YIBA || key == FAMELACK
}
