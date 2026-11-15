package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object SecurityUtils {
    private const val HASH_PREFIX = "sha256:"
    private const val SALT = "CoachMaster_Secure_Salt_v1_"
    private const val ENCRYPTED_BACKUP_PREFIX = "CM_ENC_V1:"
    private const val AES_KEY_SEED = "CoachMaster_AES256_Backup_Key_v1"

    // Legacy placeholder hashes from earlier build that need automatic normalization
    private const val LEGACY_PLACEHOLDER_PIN_HASH =
        "sha256:6b87790c146e8857357a939a409338221e89999352599a0b44e0584919002499"
    private const val LEGACY_PLACEHOLDER_PASSWORD_HASH =
        "sha256:b39285b9009d44757143e19c5303a27a8858732a460b4942352c01f079b8b643"

    // Computed directly via hashSecret so the stored default hash always matches verifySecret()
    val DEFAULT_ADMIN_PIN_HASH: String = hashSecret(String(charArrayOf('8', '8', '3', '3')))
    val DEFAULT_ADMIN_PASSWORD_HASH: String = hashSecret(String(charArrayOf('P', '@', '8', '8', '3', '3', '7')))

    fun isHashed(value: String): Boolean = value.startsWith(HASH_PREFIX)

    fun normalizeStoredHash(stored: String, isPin: Boolean): String {
        val trimmed = stored.trim()
        if (trimmed.isBlank()) {
            return if (isPin) DEFAULT_ADMIN_PIN_HASH else DEFAULT_ADMIN_PASSWORD_HASH
        }
        if (trimmed == LEGACY_PLACEHOLDER_PIN_HASH) return DEFAULT_ADMIN_PIN_HASH
        if (trimmed == LEGACY_PLACEHOLDER_PASSWORD_HASH) return DEFAULT_ADMIN_PASSWORD_HASH
        return if (isHashed(trimmed)) trimmed else hashSecret(trimmed)
    }

    fun hashSecret(raw: String): String {
        val clean = raw.trim()
        if (clean == LEGACY_PLACEHOLDER_PIN_HASH) return DEFAULT_ADMIN_PIN_HASH
        if (clean == LEGACY_PLACEHOLDER_PASSWORD_HASH) return DEFAULT_ADMIN_PASSWORD_HASH
        if (isHashed(clean)) return clean
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((SALT + clean).toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "$HASH_PREFIX$hex"
    }

    fun verifySecret(input: String, storedHashOrValue: String): Boolean {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty() || storedHashOrValue.isBlank()) return false

        val normalizedStored = when (storedHashOrValue.trim()) {
            LEGACY_PLACEHOLDER_PIN_HASH -> DEFAULT_ADMIN_PIN_HASH
            LEGACY_PLACEHOLDER_PASSWORD_HASH -> DEFAULT_ADMIN_PASSWORD_HASH
            else -> storedHashOrValue.trim()
        }

        val expectedHash = if (isHashed(normalizedStored)) {
            normalizedStored
        } else {
            hashSecret(normalizedStored)
        }

        val exactMatch = MessageDigest.isEqual(
            hashSecret(cleanInput).toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
        if (exactMatch) return true

        // Also support case-insensitive first character (e.g., 'p@88337' vs 'P@88337' on mobile keyboards)
        val upperFirst = cleanInput.replaceFirstChar { it.uppercase() }
        if (upperFirst != cleanInput) {
            return MessageDigest.isEqual(
                hashSecret(upperFirst).toByteArray(Charsets.UTF_8),
                expectedHash.toByteArray(Charsets.UTF_8)
            )
        }
        return false
    }

    /**
     * Masks student/parent phone numbers in Guest Mode (Read-Only) so guest/assistant users
     * cannot see full private contact numbers (e.g. "9876543210" -> "98765*****").
     */
    fun maskPhoneNumber(phone: String, isAdmin: Boolean): String {
        val clean = phone.trim()
        if (isAdmin || clean.isBlank()) return clean
        return if (clean.length >= 6) {
            val visiblePrefix = clean.take(5)
            val maskedSuffix = "*".repeat((clean.length - 5).coerceAtLeast(5))
            "$visiblePrefix$maskedSuffix"
        } else {
            "*****"
        }
    }

    /**
     * Encrypts backup JSON using AES-256-GCM for daily automated background backups.
     */
    fun encryptBackupPayload(plainJson: String): String {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(AES_KEY_SEED.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val cipherText = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            val encoded = Base64.getEncoder().encodeToString(combined)
            "$ENCRYPTED_BACKUP_PREFIX$encoded"
        } catch (e: Exception) {
            plainJson
        }
    }

    /**
     * Decrypts an encrypted backup payload (`CM_ENC_V1:...`) or returns plain JSON as-is.
     */
    fun decryptBackupPayloadIfNeeded(payload: String): String {
        val trimmed = payload.trim()
        if (!trimmed.startsWith(ENCRYPTED_BACKUP_PREFIX)) return trimmed
        val encoded = trimmed.removePrefix(ENCRYPTED_BACKUP_PREFIX).trim()
        val combined = Base64.getDecoder().decode(encoded)
        val iv = combined.copyOfRange(0, 12)
        val cipherText = combined.copyOfRange(12, combined.size)
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(AES_KEY_SEED.toByteArray(Charsets.UTF_8))
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
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
