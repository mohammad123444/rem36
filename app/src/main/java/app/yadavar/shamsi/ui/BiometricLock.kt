package app.yadavar.shamsi.ui

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint (biometric) lock of the app. On Android 11+ the screen lock PIN/pattern is the fallback. */
object BiometricLock {
    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

    /** True when this phone has a fingerprint (or other biometric) set up. */
    fun canUse(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFail: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("یادآور شمسی")
            .setSubtitle("برای باز کردن برنامه، اثر انگشت را بگذار")
            .setAllowedAuthenticators(authenticators())
        // With a device-credential fallback the system supplies its own cancel button.
        if (Build.VERSION.SDK_INT < 30) builder.setNegativeButtonText("انصراف")
        runCatching { prompt.authenticate(builder.build()) }.onFailure { onFail() }
    }
}
