package com.board.gamekit.bgg

class BggUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
