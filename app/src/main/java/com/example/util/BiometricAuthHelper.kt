package com.example.util

import android.content.Context
import android.content.DialogInterface
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

object BiometricAuthHelper {

    fun isHardwareBiometricSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val biometricManager = context.getSystemService(BiometricManager::class.java) ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        } else {
            @Suppress("DEPRECATION")
            biometricManager.canAuthenticate()
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS ||
                result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    fun authenticate(
        context: Context,
        instituteName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Biometric unlock requires Android 9.0 or higher. Please use your 4-digit PIN.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val biometricManager = context.getSystemService(BiometricManager::class.java)
            if (biometricManager != null) {
                val canAuth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                } else {
                    @Suppress("DEPRECATION")
                    biometricManager.canAuthenticate()
                }

                when (canAuth) {
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                        onError("No fingerprint enrolled on this device. Enroll a fingerprint in Android Settings or enter your 4-digit PIN.")
                        return
                    }
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                        onError("Biometric sensor unavailable on this device. Please use your 4-digit PIN or Admin Password.")
                        return
                    }
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        // Proceed to show hardware BiometricPrompt below
                    }
                    else -> {
                        onError("Biometric authentication unavailable. Please enter your 4-digit PIN.")
                        return
                    }
                }
            }
        }

        try {
            val executor = ContextCompat.getMainExecutor(context)
            val cancellationSignal = CancellationSignal()

            val promptBuilder = BiometricPrompt.Builder(context)
                .setTitle("${instituteName.ifBlank { "CoachMaster" }} Admin Unlock")
                .setSubtitle("Touch the fingerprint sensor or use Face Unlock")
                .setNegativeButton(
                    "Use 4-Digit PIN",
                    executor,
                    DialogInterface.OnClickListener { _, _ ->
                        cancellationSignal.cancel()
                    }
                )

            val biometricPrompt = promptBuilder.build()
            biometricPrompt.authenticate(
                cancellationSignal,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        ) {
                            onError(errString?.toString() ?: "Biometric authentication error.")
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("Biometric not recognized. Try again or use 4-digit PIN.")
                    }
                }
            )
        } catch (e: Exception) {
            onError("Could not start biometric sensor: ${e.localizedMessage ?: "Use 4-digit PIN"}")
        }
    }
}
