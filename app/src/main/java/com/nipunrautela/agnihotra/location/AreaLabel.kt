package com.nipunrautela.agnihotra.location

/** Prefer a neighbourhood and city; never use a house number or full street address as the alias. */
internal fun areaLabel(subLocality: String?, locality: String?, district: String?, region: String?): String? {
    fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }
    val area = subLocality.clean()
    val city = locality.clean()
    return if (area != null && city != null && !area.equals(city, ignoreCase = true)) "$area, $city"
    else area ?: city ?: district.clean() ?: region.clean()
}
