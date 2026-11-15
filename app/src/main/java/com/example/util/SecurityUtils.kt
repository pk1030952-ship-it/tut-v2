package com.example.util

import java.security.MessageDigest

object SecurityUtils {
    private const val HASH_PREFIX = "sha256:"
    private const val SALT = "CoachMaster_Secure_Salt_v1_"

    // Pre-computed SHA-256 hashes for initial first-install defaults so no plaintext PIN or password
    // ever appears in source code or database tables.
    const val DEFAULT_ADMIN_PIN_HASH = "sha256:6b87790c146e8857357a939a409338221e89999352599a0b44e0584919002499"
    const val DEFAULT_ADMIN_PASSWORD_HASH = "sha256:b39285b9009d44757143e19c5303a27a8858732a460b4942352c01f079b8b643"

    fun isHashed(value: String): Boolean = value.startsWith(HASH_PREFIX)

    fun hashSecret(raw: String): String {
        val clean = raw.trim()
        if (isHashed(clean)) return clean
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((SALT + clean).toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "$HASH_PREFIX$hex"
    }

    fun verifySecret(input: String, storedHashOrValue: String): Boolean {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty() || storedHashOrValue.isBlank()) return false
        val expectedHash = if (isHashed(storedHashOrValue)) {
            storedHashOrValue
        } else {
            hashSecret(storedHashOrValue)
        }
        val inputHash = hashSecret(cleanInput)
        return MessageDigest.isEqual(
            inputHash.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun isValidTenDigitPhone(phone: String): Boolean {
        val trimmed = phone.trim()
        return trimmed.length == 10 && trimmed.all { it.isDigit() }
    }

    fun isValidPositiveAmount(amountStr: String): Boolean {
        val value = amountStr.trim().toDoubleOrNull() ?: return false
        return value > 0.0 && !value.isNaN() && !value.isInfinite()
    }

    fun validateStudentInput(
        rollNo: String,
        name: String,
        phone: String,
        monthlyFeeStr: String
    ): String? {
        if (rollNo.trim().isBlank()) {
            return "Student Roll No / Admission ID cannot be blank."
        }
        if (name.trim().isBlank()) {
            return "Student Full Name cannot be blank."
        }
        if (!isValidTenDigitPhone(phone)) {
            return "Mobile Number must be a valid 10-digit numeric number."
        }
        if (!isValidPositiveAmount(monthlyFeeStr)) {
            return "Monthly Fee must be a positive number greater than 0."
        }
        return null
    }
}
