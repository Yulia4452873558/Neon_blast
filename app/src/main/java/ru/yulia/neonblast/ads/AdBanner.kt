package ru.yulia.neonblast.ads

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData

/** Height cap for the in-game banner. */
private const val MAX_BANNER_HEIGHT_DP = 100

/**
 * Adaptive sticky banner across the full width. If the sticky size the SDK picks would be
 * taller than [MAX_BANNER_HEIGHT_DP], an inline size capped at that height is used instead.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val widthDp = maxWidth.value.toInt()
        val banner = remember(widthDp) {
            val sticky = BannerAdSize.sticky(context, widthDp)
            val size = if (sticky.height in 1..MAX_BANNER_HEIGHT_DP) sticky else BannerAdSize.inline(context, widthDp, MAX_BANNER_HEIGHT_DP)
            BannerAdView(context).apply {
                setAdSize(size)
                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() = Unit
                    override fun onAdFailedToLoad(error: AdRequestError) {
                        Log.w("NeonAds", "banner failed: ${error.description}")
                    }
                    override fun onAdClicked() = Unit
                    override fun onImpression(impressionData: ImpressionData?) = Unit
                })
                loadAd(AdRequest.Builder(AdUnits.banner).build())
            }
        }
        DisposableEffect(banner) {
            onDispose { banner.destroy() }
        }
        AndroidView(
            factory = { banner },
            modifier = Modifier.fillMaxWidth().heightIn(max = MAX_BANNER_HEIGHT_DP.dp),
        )
    }
}
