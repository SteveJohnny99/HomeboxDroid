package it.stevej.homeboxdroid

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Fornisce a Coil l'ImageLoader con auth (iniettato da Hilt), cosi' AsyncImage
 * puo' caricare gli allegati protetti dell'istanza Homebox.
 */
@HiltAndroidApp
class HomeboxApp : Application(), ImageLoaderFactory {

    @Inject lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader
}
