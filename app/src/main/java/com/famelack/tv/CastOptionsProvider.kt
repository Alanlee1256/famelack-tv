package com.famelack.tv

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider

class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder()
            .setReceiverApplicationId("")
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalOptions(context: Context): Map<String, String> {
        return emptyMap()
    }
}
