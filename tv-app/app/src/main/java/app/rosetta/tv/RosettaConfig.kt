package app.rosetta.tv

import android.content.Context
import app.rosetta.tv.data.CloudRepository
import app.rosetta.tv.data.LocalRepository
import app.rosetta.tv.data.RosettaRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Build-time switch between the on-device household engine and the deployed agent backend.
 * Set -ProsettaApiUrl / -ProsettaWebUrl after `cdk deploy` (see README).
 */
object RosettaConfig {
    val apiUrl: String = BuildConfig.ROSETTA_API_URL
    val webUrl: String = BuildConfig.ROSETTA_WEB_URL

    fun repository(context: Context, scope: CoroutineScope): RosettaRepository =
        if (apiUrl.isBlank()) LocalRepository(scope) else CloudRepository(context, apiUrl, scope, BuildConfig.ROSETTA_CODE)

    fun joinUrl(code: String): String =
        if (webUrl.isBlank()) "https://rosetta.family/join?code=$code" else "${webUrl.trimEnd('/')}/kid.html?code=$code"

    /** The parent page for this household, or null when the TV runs offline (there is no cloud for it to talk to). */
    fun parentUrl(code: String): String? = webUrl.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/parent.html?code=$code" }
}
