package com.dmerzliakov.freezekit

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform