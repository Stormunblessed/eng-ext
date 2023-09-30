// use an integer for version numbers
version = 4


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

//    description = "Uses TMDB"
    authors = listOf("Blatzar")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AnimeMovie"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=ask4movie.mx&sz=%size%"
}