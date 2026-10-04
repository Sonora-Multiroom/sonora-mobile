package sonora.multiroom.mobile.domain

/** `[]` -> "", `[A]` -> "A", `[A, B]` -> "A and B", `[A, B, C]` -> "A, B and C". */
fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}
