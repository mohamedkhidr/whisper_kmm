package com.khidrew.notelydesktop

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform