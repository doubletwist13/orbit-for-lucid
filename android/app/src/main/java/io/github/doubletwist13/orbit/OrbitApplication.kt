package io.github.doubletwist13.orbit

import android.app.Application

class OrbitApplication : Application() {
    val controller by lazy { Controller(this) }
}
