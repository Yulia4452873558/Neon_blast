package ru.yulia.neonblast.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.yulia.neonblast.BuildConfig
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import com.yandex.mobile.ads.rewarded.Reward
import com.yandex.mobile.ads.rewarded.RewardedAd
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener
import com.yandex.mobile.ads.rewarded.RewardedAdLoader

/** Ad unit ids: demo units in debug builds, the store flavor's real units otherwise. */
object AdUnits {
    val banner: String = if (BuildConfig.DEBUG) "demo-banner-yandex" else BuildConfig.AD_UNIT_BANNER
    val interstitial: String = if (BuildConfig.DEBUG) "demo-interstitial-yandex" else BuildConfig.AD_UNIT_INTERSTITIAL
    val rewarded: String = if (BuildConfig.DEBUG) "demo-rewarded-yandex" else BuildConfig.AD_UNIT_REWARDED
}

private const val TAG = "NeonAds"
private const val RETRY_MS = 30_000L

/**
 * Keeps one interstitial and one rewarded ad preloaded and shows them on demand.
 * Every show call always ends with its completion callback, even when no ad is available,
 * so the game flow never gets stuck waiting for an ad.
 */
class AdsManager(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val interstitialLoader = InterstitialAdLoader(context)
    private val rewardedLoader = RewardedAdLoader(context)

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false

    /** True while an ad covers the screen. */
    var isShowing = false
        private set

    /** Observable so the UI can say "ad is loading" instead of offering a dead button. */
    var rewardedReady by mutableStateOf(false)
        private set

    fun preload() {
        loadInterstitial()
        loadRewarded()
    }

    // --- interstitial ---------------------------------------------------------

    private fun loadInterstitial() {
        if (interstitial != null || interstitialLoading) return
        interstitialLoading = true
        interstitialLoader.loadAd(
            AdRequest.Builder(AdUnits.interstitial).build(),
            object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    interstitialLoading = false
                    Log.w(TAG, "interstitial failed: ${error.description}")
                    handler.postDelayed(::loadInterstitial, RETRY_MS)
                }
            },
        )
    }

    /** Shows an interstitial if one is ready, then calls [onClosed]. */
    fun showInterstitial(activity: Activity?, onClosed: () -> Unit) {
        val ad = interstitial
        if (ad == null || activity == null || activity.isFinishing || isShowing) {
            loadInterstitial()
            onClosed()
            return
        }
        interstitial = null
        isShowing = true
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            isShowing = false
            ad.setAdEventListener(null)
            loadInterstitial()
            onClosed()
        }
        ad.setAdEventListener(object : InterstitialAdEventListener {
            override fun onAdShown() = Unit
            override fun onAdFailedToShow(adError: AdError) {
                Log.w(TAG, "interstitial show failed: ${adError.description}")
                finish()
            }
            override fun onAdDismissed() = finish()
            override fun onAdClicked() = Unit
            override fun onAdImpression(impressionData: ImpressionData?) = Unit
        })
        ad.show(activity)
    }

    // --- rewarded ---------------------------------------------------------------

    private fun loadRewarded() {
        if (rewarded != null || rewardedLoading) return
        rewardedLoading = true
        rewardedLoader.loadAd(
            AdRequest.Builder(AdUnits.rewarded).build(),
            object : RewardedAdLoadListener {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewarded = ad
                    rewardedReady = true
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    rewardedLoading = false
                    Log.w(TAG, "rewarded failed: ${error.description}")
                    handler.postDelayed(::loadRewarded, RETRY_MS)
                }
            },
        )
    }

    /**
     * Shows a rewarded ad. [onReward] fires once if the user earned the reward;
     * [onClosed] always fires when the ad is gone. Returns false if no ad was ready.
     */
    fun showRewarded(activity: Activity?, onReward: () -> Unit, onClosed: () -> Unit = {}): Boolean {
        val ad = rewarded
        if (ad == null || activity == null || activity.isFinishing || isShowing) {
            loadRewarded()
            return false
        }
        rewarded = null
        rewardedReady = false
        isShowing = true
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            isShowing = false
            ad.setAdEventListener(null)
            loadRewarded()
            onClosed()
        }
        ad.setAdEventListener(object : RewardedAdEventListener {
            override fun onAdShown() = Unit
            override fun onAdFailedToShow(adError: AdError) {
                Log.w(TAG, "rewarded show failed: ${adError.description}")
                finish()
            }
            override fun onAdDismissed() = finish()
            override fun onAdClicked() = Unit
            override fun onAdImpression(impressionData: ImpressionData?) = Unit
            override fun onRewarded(reward: Reward) = onReward()
        })
        ad.show(activity)
        return true
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
