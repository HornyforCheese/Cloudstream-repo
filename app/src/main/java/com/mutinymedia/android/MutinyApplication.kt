package com.mutinymedia.android

import android.app.Application
import com.mutinymedia.android.data.CloudStreamHost

class MutinyApplication : Application() {
    val cloudStreamHost by lazy { CloudStreamHost(this) }
}
