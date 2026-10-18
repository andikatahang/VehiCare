package com.vehicare

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform