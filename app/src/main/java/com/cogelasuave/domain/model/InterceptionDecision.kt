package com.cogelasuave.domain.model

/** The choice the user makes once the wait timer ends. */
enum class InterceptionDecision {
    /** "Abrir" – let the app through. */
    OPENED,

    /** "Mejor no" – go back home. */
    DISMISSED,
}
