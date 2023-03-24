package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty

data class LinksQuery(
    @JsonProperty("data") val data: LinkData? = LinkData()
)

data class LinkData(
    @JsonProperty("episode") val episode: Episode? = Episode()
)

data class SourceUrls(
    @JsonProperty("sourceUrl") val sourceUrl: String? = null,
    @JsonProperty("priority") val priority: Int? = null,
    @JsonProperty("sourceName") val sourceName: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("className") val className: String? = null,
    @JsonProperty("streamerId") val streamerId: String? = null
)

data class Episode(
//    @JsonProperty("episodeString") val episodeString: String? = null,
//  @JsonProperty("uploadDate"    ) val uploadDate    : UploadDate?           = UploadDate(),
    @JsonProperty("sourceUrls") val sourceUrls: ArrayList<SourceUrls> = arrayListOf(),
//    @JsonProperty("thumbnail") val thumbnail: String? = null,
//    @JsonProperty("notes") val notes: String? = null,
//  @JsonProperty("show"          ) val show          : Show?                 = Show(),
//    @JsonProperty("pageStatus") val pageStatus: PageStatus? = PageStatus(),
//    @JsonProperty("episodeInfo") val episodeInfo: EpisodeInfo? = EpisodeInfo(),
//    @JsonProperty("versionFix") val versionFix: String? = null,
//    @JsonProperty("__typename") val _typename: String? = null
)

//data class EpisodeInfo(
//    @JsonProperty("notes") val notes: String? = null,
//    @JsonProperty("thumbnails") val thumbnails: ArrayList<String> = arrayListOf(),
//    @JsonProperty("vidInforssub") val vidInforssub: VidInforssub? = VidInforssub(),
////  @JsonProperty("uploadDates"  ) val uploadDates  : UploadDates?      = UploadDates(),
//    @JsonProperty("vidInforsdub") val vidInforsdub: String? = null,
//    @JsonProperty("vidInforsraw") val vidInforsraw: String? = null,
//    @JsonProperty("description") val description: String? = null,
//    @JsonProperty("__typename") val _typename: String? = null
//)

//data class VidInforssub(
//    @JsonProperty("vidResolution") val vidResolution: Int? = null,
//    @JsonProperty("vidPath") val vidPath: String? = null,
//    @JsonProperty("vidSize") val vidSize: Int? = null,
//    @JsonProperty("vidDuration") val vidDuration: Double? = null
//)