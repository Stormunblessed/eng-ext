// use an integer for version numbers
version = 2


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them
    description = "Allows you to use stremio addons. Requires setup. (Torrents and old api does not work)"

    // description = "Lorem Ipsum"
    authors = listOf("Cloudburst")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf("Others")

    iconUrl = "https://www.google.com/s2/favicons?domain=www.stremio.com&sz=%size%"
}