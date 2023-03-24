package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty

data class PopularQuery(
    @JsonProperty("data") val data: Data? = Data()
)


data class AvailableEpisodes(
    @JsonProperty("sub") val sub: Int? = null,
    @JsonProperty("dub") val dub: Int? = null,
    @JsonProperty("raw") val raw: Int? = null
)

data class Sub(
    @JsonProperty("hour") val hour: Int? = null,
    @JsonProperty("minute") val minute: Int? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("month") val month: Int? = null,
    @JsonProperty("date") val date: Int? = null
)

data class LastEpisodeDate(
    @JsonProperty("dub") val dub: Sub? = Sub(),
    @JsonProperty("sub") val sub: Sub? = Sub(),
    @JsonProperty("raw") val raw: Sub? = Sub()
)

data class AnyCard(
    @JsonProperty("_id") val Id: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("englishName") val englishName: String? = null,
    @JsonProperty("nativeName") val nativeName: String? = null,
    @JsonProperty("availableEpisodes") val availableEpisodes: AvailableEpisodes? = AvailableEpisodes(),
    @JsonProperty("score") val score: Double? = null,
    @JsonProperty("lastEpisodeDate") val lastEpisodeDate: LastEpisodeDate? = LastEpisodeDate(),
    @JsonProperty("thumbnail") val thumbnail: String? = null,
    @JsonProperty("lastChapterDate") val lastChapterDate: String? = null,
    @JsonProperty("availableChapters") val availableChapters: String? = null,
    @JsonProperty("__typename") val _typename: String? = null
)

data class PageStatus(
    @JsonProperty("_id") val Id: String? = null,
    @JsonProperty("views") val views: String? = null,
    @JsonProperty("showId") val showId: String? = null,
    @JsonProperty("rangeViews") val rangeViews: String? = null,
    @JsonProperty("isManga") val isManga: Boolean? = null,
    @JsonProperty("__typename") val _typename: String? = null
)


data class Recommendations(
    @JsonProperty("anyCard") val anyCard: AnyCard? = AnyCard(),
    @JsonProperty("pageStatus") val pageStatus: PageStatus? = PageStatus(),
    @JsonProperty("__typename") val _typename: String? = null
)

data class QueryPopular(
    @JsonProperty("total") val total: Int? = null,
    @JsonProperty("recommendations") val recommendations: ArrayList<Recommendations> = arrayListOf(),
    @JsonProperty("__typename") val _typename: String? = null
)

data class Data(
    @JsonProperty("queryPopular") val queryPopular: QueryPopular? = QueryPopular()
)
