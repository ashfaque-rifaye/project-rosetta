package app.rosetta.tv

import android.content.Context
import app.rosetta.tv.data.CloudRepository
import app.rosetta.tv.data.DemoRepository
import app.rosetta.tv.data.RosettaRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Build-time switch between the bundled demo household and the deployed agent backend.
 * Set ROSETTA_API_URL / ROSETTA_WEB_URL in gradle.properties (or -P) after `cdk deploy`.
 */
object RosettaConfig {
    val apiUrl: String = BuildConfig.ROSETTA_API_URL
    val webUrl: String = BuildConfig.ROSETTA_WEB_URL

    fun repository(context: Context, scope: CoroutineScope): RosettaRepository =
        if (apiUrl.isBlank()) DemoRepository(scope) else CloudRepository(context, apiUrl, scope)

    /** What the TV's QR encodes: the phone web page plus the household's join code. */
    fun joinUrl(code: String): String =
        if (webUrl.isBlank()) "ROSETTA:$code" else "${webUrl.trimEnd('/')}/kid.html?code=$code"
}
