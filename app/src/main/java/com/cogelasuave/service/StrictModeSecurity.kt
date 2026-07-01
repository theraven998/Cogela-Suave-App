package com.cogelasuave.service

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Helpers for the strict-mode "master password".
 *
 * The password is a long numeric code the user is meant to write down on paper
 * and re-type digit by digit to unlock strict mode early. We only ever persist a
 * SHA-256 hash, so reading the prefs file does not reveal the code.
 */
object StrictModeSecurity {

    /** Number of digits in a generated master password. Long enough to be a pain. */
    const val PASSWORD_LENGTH = 16

    private val random = SecureRandom()

    /** Generates a random numeric master password of [PASSWORD_LENGTH] digits. */
    fun generatePassword(): String =
        buildString(PASSWORD_LENGTH) {
            repeat(PASSWORD_LENGTH) { append(random.nextInt(10)) }
        }

    /** Groups a raw password in blocks of 4 for readable display ("1234 5678 ..."). */
    fun formatForDisplay(password: String): String =
        password.chunked(4).joinToString(" ")

    /** Strips spaces/dashes so user-typed input matches the stored hash. */
    fun normalizeInput(input: String): String = input.filter { it.isDigit() }

    /** SHA-256 hex of [password]. */
    fun hash(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
