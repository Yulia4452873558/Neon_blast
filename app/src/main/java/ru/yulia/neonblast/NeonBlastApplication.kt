package ru.yulia.neonblast

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import ru.yulia.neonblast.ads.AdsManager
import com.yandex.mobile.ads.common.YandexAds

class NeonBlastApplication : Application() {
    lateinit var ads: AdsManager
        private set

    /** MainActivity while it is on screen (resumed), otherwise null. */
    private var foregroundActivity: Activity? = null
    private var startedActivities = 0

    /**
     * On a cold start the ad is usually still loading when the game appears.
     * We wait this long for it; if it arrives later we skip it so it never
     * interrupts a game the player has already started.
     */
    private var coldStartWaitUntil = 0L

    override fun onCreate() {
        super.onCreate()
        ads = AdsManager(this)
        coldStartWaitUntil = SystemClock.elapsedRealtime() + COLD_START_WAIT_MS
        ads.onAppOpenLoaded = ::tryColdStartAd
        registerActivityLifecycleCallbacks(AppOpenCallbacks())
        YandexAds.initialize(this) {
            // the SDK is ready: warm up the full-screen ads so they show instantly later
            ads.preload()
        }
    }

    /** Shows the launch ad once, if both the ad and the screen are ready within the wait window. */
    private fun tryColdStartAd() {
        if (SystemClock.elapsedRealtime() >= coldStartWaitUntil) return
        if (ads.showAppOpen(foregroundActivity)) coldStartWaitUntil = 0L
    }

    private inner class AppOpenCallbacks : ActivityLifecycleCallbacks {
        private var returningFromBackground = false

        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            // only when the whole app comes back from background into our own screen,
            // not when an ad's activity or a browser opened from an ad closes
            if (startedActivities == 1 && activity is MainActivity && returningFromBackground) {
                returningFromBackground = false
                coldStartWaitUntil = 0L
                ads.showAppOpen(activity)
            }
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities--
            if (startedActivities == 0 && !activity.isChangingConfigurations) {
                returningFromBackground = true
            }
        }

        override fun onActivityResumed(activity: Activity) {
            if (activity is MainActivity) {
                foregroundActivity = activity
                tryColdStartAd()
            }
        }

        override fun onActivityPaused(activity: Activity) {
            if (foregroundActivity === activity) foregroundActivity = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private companion object {
        const val COLD_START_WAIT_MS = 5_000L
    }
}
