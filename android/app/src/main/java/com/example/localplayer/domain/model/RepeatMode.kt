package com.example.localplayer.domain.model

/** Режим повтора очереди. */
enum class RepeatMode {
    /** Выключен: доиграли последний трек — остановились. */
    OFF,

    /** Повторять всю очередь. */
    ALL,

    /** Повторять текущий трек. */
    ONE;

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}
