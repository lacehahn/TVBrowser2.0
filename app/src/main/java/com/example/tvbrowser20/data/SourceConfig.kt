package com.example.tvbrowser20.data

/**
 * Central configuration for all sources and channels.
 *
 * HOW TO ADD A NEW SOURCE:
 *   1. Add a new Source(...) entry to SOURCES.
 *   2. List allowedDomains so the URL whitelist allows it.
 *   3. Set jsKey to an existing JsKey constant (or add a new one in JsInjector).
 *
 * HOW TO ADD A NEW CHANNEL:
 *   1. Add a Channel(...) to the relevant source's channels list.
 *   2. Provide channelNum, name, sub, and url.
 */
object SourceConfig {

    private val YIBA_DOMAINS = listOf(
        "yibababa.com",
        "www.yibababa.com",
        "web.sdk.qcloud.com",
        "pagead2.googlesyndication.com"
    )

    private val FAMELACK_DOMAINS = listOf(
        "famelack.com",
        "www.famelack.com",
        "raw.githubusercontent.com",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com",
        "googlevideo.com",
        "ytimg.com",
        "cloudfront.net",
    )

    val SOURCES: List<Source> = listOf(
        cctvSource(),
        yibaCctv5Source(),
        famelackJpSource(),
        famelackCnSource(),
        famelackUsSource(),
        famelackUkSource(),
    )

    // ── CCTV 官方源 ───────────────────────────────────────────────────────────
    private fun cctvSource() = Source(
        id = "cctv_official",
        label = "CCTV官方",
        baseUrl = "https://tv.cctv.com",
        jsKey = JsKey.CCTV,
        allowedDomains = listOf(
            "tv.cctv.com",
            "cctv.com",
            "vdn.live.cntv.cn",
            "live.cntv.cn",
            "hls.cntv.cn",
            "g2.cctv.cn"
        ),
        channels = listOf(
            Channel("cctv1",  "CCTV-1",  "综合频道",  "新闻·综合", "https://tv.cctv.com/live/cctv1/m/index.shtml"),
            Channel("cctv2",  "CCTV-2",  "财经频道",  "财经·资讯", "https://tv.cctv.com/live/cctv2/m/index.shtml"),
            Channel("cctv3",  "CCTV-3",  "综艺频道",  "文娱·综艺", "https://tv.cctv.com/live/cctv3/m/index.shtml"),
            Channel("cctv4",  "CCTV-4",  "中文国际",  "国际·中文", "https://tv.cctv.com/live/cctv4/m/index.shtml"),
            Channel("cctv5",  "CCTV-5",  "体育频道",  "体育·赛事", "https://tv.cctv.com/live/cctv5/m/index.shtml"),
            Channel("cctv5p", "CCTV-5+", "体育赛事",  "足球·篮球", "https://tv.cctv.com/live/cctv5plus/m/index.shtml"),
            Channel("cctv6",  "CCTV-6",  "电影频道",  "电影·影视", "https://tv.cctv.com/live/cctv6/m/index.shtml"),
            Channel("cctv7",  "CCTV-7",  "国防军事",  "军事·科技", "https://tv.cctv.com/live/cctv7/m/index.shtml"),
            Channel("cctv8",  "CCTV-8",  "电视剧频道","国产·剧集", "https://tv.cctv.com/live/cctv8/m/index.shtml"),
            Channel("cctv9",  "CCTV-9",  "纪录频道",  "纪录·探索", "https://tv.cctv.com/live/cctv9/m/index.shtml"),
            Channel("cctv10", "CCTV-10", "科教频道",  "科技·教育", "https://tv.cctv.com/live/cctv10/m/index.shtml"),
            Channel("cctv11", "CCTV-11", "戏曲频道",  "京剧·戏曲", "https://tv.cctv.com/live/cctv11/m/index.shtml"),
            Channel("cctv12", "CCTV-12", "社会与法",  "法制·社会", "https://tv.cctv.com/live/cctv12/m/index.shtml"),
            Channel("cctv13", "CCTV-13", "新闻频道",  "时事·新闻", "https://tv.cctv.com/live/cctv13/m/index.shtml"),
            Channel("cctv14", "CCTV-14", "少儿频道",  "动画·少儿", "https://tv.cctv.com/live/cctv14/m/index.shtml"),
            Channel("cctv15", "CCTV-15", "音乐频道",  "音乐·演出", "https://tv.cctv.com/live/cctv15/m/index.shtml"),
            Channel("cctv16", "CCTV-16", "奥林匹克",  "体育·奥运", "https://tv.cctv.com/live/cctv16/m/index.shtml"),
            Channel("cctv17", "CCTV-17", "农业农村",  "农业·乡村", "https://tv.cctv.com/live/cctv17/m/index.shtml"),
        )
    )

    // ── 体育赛事 (yibababa) ───────────────────────────────────────────────────
    private fun yibaCctv5Source() = Source(
        id = "yibababa_cctv5",
        label = "体育赛事",
        baseUrl = "https://yibababa.com",
        jsKey = JsKey.YIBA,
        allowedDomains = YIBA_DOMAINS,
        channels = listOf(
            Channel("y_cctv5", "CCTV-5", "选择载入更多", "体育·赛事", "https://yibababa.com/tv/cctv5/"),
        )
    )

    // ── 日本节目 (Famelack) ───────────────────────────────────────────────────
    private fun famelackJpSource() = Source(
        id = "famelack_jp",
        label = "日本节目",
        baseUrl = "https://famelack.com",
        jsKey = JsKey.FAMELACK,
        allowedDomains = FAMELACK_DOMAINS,
        channels = listOf(
            Channel(
                "f_jp",
                "JP",
                "选择载入更多",
                "日本·直播",
                "https://famelack.com/tv/jp"
            ),
        )
    )

    // ── 中国节目 (Famelack) ───────────────────────────────────────────────────
    private fun famelackCnSource() = Source(
        id = "famelack_cn",
        label = "中国节目",
        baseUrl = "https://famelack.com",
        jsKey = JsKey.FAMELACK,
        allowedDomains = FAMELACK_DOMAINS,
        channels = listOf(
            Channel(
                "f_cn",
                "CN",
                "选择载入更多",
                "中国·直播",
                "https://famelack.com/tv/cn/zRxr2ZAtzWPYCP"
            ),
        )
    )

    // ── 美国节目 (Famelack) ───────────────────────────────────────────────────
    private fun famelackUsSource() = Source(
        id = "famelack_us",
        label = "美国节目",
        baseUrl = "https://famelack.com",
        jsKey = JsKey.FAMELACK,
        allowedDomains = FAMELACK_DOMAINS,
        channels = listOf(
            Channel(
                "f_us",
                "US",
                "选择载入更多",
                "美国·直播",
                "https://famelack.com/tv/us"
            ),
        )
    )

    // ── 英国节目 (Famelack) ───────────────────────────────────────────────────
    private fun famelackUkSource() = Source(
        id = "famelack_uk",
        label = "英国节目",
        baseUrl = "https://famelack.com",
        jsKey = JsKey.FAMELACK,
        allowedDomains = FAMELACK_DOMAINS,
        channels = listOf(
            Channel(
                "f_uk",
                "UK",
                "选择载入更多",
                "英国·直播",
                "https://famelack.com/tv/uk"
            ),
        )
    )
}
