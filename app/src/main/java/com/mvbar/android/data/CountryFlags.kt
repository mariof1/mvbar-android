package com.mvbar.android.data

object CountryFlags {
    private val COUNTRY_CODES = mapOf(
        "Poland" to "pl",
        "France" to "fr",
        "Germany" to "de",
        "Spain" to "es",
        "United Kingdom" to "gb",
        "UK" to "gb",
        "United States" to "us",
        "USA" to "us",
        "Japan" to "jp",
        "South Korea" to "kr",
        "Korea" to "kr",
        "China" to "cn",
        "India" to "in",
        "Brazil" to "br",
        "Mexico" to "mx",
        "Canada" to "ca",
        "Australia" to "au",
        "Italy" to "it",
        "Sweden" to "se",
        "Norway" to "no",
        "Denmark" to "dk",
        "Finland" to "fi",
        "Netherlands" to "nl",
        "Belgium" to "be",
        "Austria" to "at",
        "Switzerland" to "ch",
        "Ireland" to "ie",
        "Portugal" to "pt",
        "Lithuania" to "lt",
        "Russia" to "ru",
        "Ukraine" to "ua",
        "Jamaica" to "jm",
        "Cuba" to "cu",
        "Argentina" to "ar",
        "Colombia" to "co",
        "Nigeria" to "ng",
        "South Africa" to "za",
        "Egypt" to "eg",
        "Israel" to "il",
        "Turkey" to "tr",
        "Greece" to "gr",
        "Romania" to "ro",
        "Czech Republic" to "cz",
        "Hungary" to "hu",
        "Iceland" to "is",
        "New Zealand" to "nz",
        "Philippines" to "ph",
        "Indonesia" to "id",
        "Thailand" to "th",
        "Vietnam" to "vn",
        "Malaysia" to "my",
        "Singapore" to "sg",
    )

    fun getCode(country: String): String? = COUNTRY_CODES[country]

    /** Returns flag image URL from flagcdn.com, or null if country not mapped */
    fun flagUrl(country: String): String? {
        val code = getCode(country) ?: return null
        return "https://flagcdn.com/w160/$code.png"
    }
}
