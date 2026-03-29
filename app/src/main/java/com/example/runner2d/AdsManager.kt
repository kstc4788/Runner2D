package com.example.runner2d

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdsConfig {
    // Google official test ad unit IDs.
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
}

class RunnerAdsManager(
    private val context: Context,
) {
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    val isRewardedReady: Boolean
        get() = rewardedAd != null

    fun preloadAll() {
        loadInterstitial()
        loadRewarded()
    }

    private fun loadInterstitial() {
        InterstitialAd.load(
            context,
            AdsConfig.INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            },
        )
    }

    private fun loadRewarded() {
        RewardedAd.load(
            context,
            AdsConfig.REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            },
        )
    }

    fun maybeShowInterstitial(
        activity: Activity,
        completedRuns: Int,
        frequencyCap: Int = 4,
    ) {
        if (completedRuns <= 0 || completedRuns % frequencyCap != 0) {
            return
        }
        val ad = interstitialAd ?: return
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                loadInterstitial()
            }
        }
        ad.show(activity)
    }

    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdClosed: () -> Unit,
    ): Boolean {
        val ad = rewardedAd ?: return false
        rewardedAd = null
        var rewarded = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (rewarded) {
                    onRewardEarned()
                }
                onAdClosed()
                loadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                onAdClosed()
                loadRewarded()
            }
        }
        ad.show(activity) {
            rewarded = true
        }
        return true
    }
}

@Composable
fun RunnerBannerAd(
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdsConfig.BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
