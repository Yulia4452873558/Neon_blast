package ru.yulia.neonblast

import android.app.Application
import ru.yulia.neonblast.ads.AdsManager
import com.yandex.mobile.ads.common.YandexAds

class NeonBlastApplication : Application() {
    lateinit var ads: AdsManager
        private set

    override fun onCreate() {
        super.onCreate()
        ads = AdsManager(this)
        YandexAds.initialize(this) {
            // the SDK is ready: warm up the full-screen ads so they show instantly later
            ads.preload()
        }
    }
}
