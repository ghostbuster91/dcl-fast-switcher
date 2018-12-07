package io.ghostbuster91.docker.compose.logger

object Colors {
    private val ANSI_RESET = "\u001B[0m"
    private val ANSI_BLACK = "\u001B[30m"
    private val ANSI_RED = "\u001B[31m"
    private val ANSI_GREEN = "\u001B[32m"
    private val ANSI_YELLOW = "\u001B[33m"
    private val ANSI_BLUE = "\u001B[34m"
    private val ANSI_PURPLE = "\u001B[35m"
    private val ANSI_CYAN = "\u001B[36m"
    private val ANSI_WHITE = "\u001B[37m"

    fun String.red(): String {
        return ANSI_RED + this + ANSI_RESET
    }

    fun String.black(): String {
        return ANSI_BLACK + this + ANSI_RESET
    }

    fun String.green(): String {
        return ANSI_GREEN + this + ANSI_RESET
    }

    fun String.yellow(): String {
        return ANSI_YELLOW + this + ANSI_RESET
    }

    fun String.blue(): String {
        return ANSI_BLUE + this + ANSI_RESET
    }

    fun String.purple():String {
        return ANSI_PURPLE + this + ANSI_RESET
    }

    fun String.cyan(): String{
        return ANSI_CYAN + this + ANSI_RESET
    }

    fun String.white(): String {
        return ANSI_WHITE + this + ANSI_RESET
    }
}

