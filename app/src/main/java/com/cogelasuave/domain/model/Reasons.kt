package com.cogelasuave.domain.model

/**
 * The reasons the user can attach when they decide to open a watched app. The
 * four fixed values double as the stored keys, so they must stay stable. Any
 * other free-text reason ("Otra") is stored verbatim and shows up as its own
 * category in the stats.
 */
object Reasons {
    const val OCIO = "Ocio"
    const val DM_AMIGOS = "Ver DM amigos"
    const val PUBLICAR = "Publicar"
    const val INSPIRACION = "Inspiración"

    /** Presented as quick buttons, in display order. */
    val FIXED = listOf(OCIO, DM_AMIGOS, PUBLICAR, INSPIRACION)
}
