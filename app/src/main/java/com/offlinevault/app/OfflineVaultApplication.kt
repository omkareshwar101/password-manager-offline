package com.offlinevault.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.offlinevault.app.di.AppContainer

class OfflineVaultApplication : Application(), DefaultLifecycleObserver, ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                container.sessionManager.onScreenOff()
            }
        }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStart(owner: LifecycleOwner) {
        container.sessionManager.onForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        container.sessionManager.onBackground()
    }

    /**
     * Coil is used strictly for app-private files. Disk caching is disabled so
     * account images are not duplicated into another cache file.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.08)
                .build()
        }
        .diskCachePolicy(CachePolicy.DISABLED)
        .diskCache(null)
        .build()
}
