package com.board.gamekit

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform