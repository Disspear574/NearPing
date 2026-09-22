package com.disspear574.earshot.linux

import java.util.Locale

/**
 * Английский обязателен, русский идёт вторым. Ресурсов Compose Desktop здесь
 * не заводим ради десятка строк, но правило то же: ни одной строки в коде экрана.
 */
data class Strings(
    val appName: String = "Earshot",
    val yourName: String,
    val contacts: String,
    val nearby: String,
    val pairingRequests: String,
    val nobodyNearby: String,
    val online: String,
    val offline: String,
    val pausedUntil: String,
    val add: String,
    val accept: String,
    val decline: String,
    val forget: String,
    val pause: String,
    val paused: String,
    val resume: String,
    val minutes15: String,
    val hour1: String,
    val untilTomorrow: String,
    val isCalling: String,
    val urgent: String,
    val gotIt: String,
    val holdForUrgent: String,
    val quit: String,
) {
    companion object {
        private val english = Strings(
            yourName = "Your name",
            contacts = "CONTACTS",
            nearby = "NEARBY",
            pairingRequests = "PAIRING REQUESTS",
            nobodyNearby = "Nobody else on this network yet. Open Earshot on another machine.",
            online = "online",
            offline = "offline",
            pausedUntil = "paused",
            add = "Add",
            accept = "Accept",
            decline = "Decline",
            forget = "Forget",
            pause = "Pause",
            paused = "Paused",
            resume = "Resume",
            minutes15 = "15 minutes",
            hour1 = "1 hour",
            untilTomorrow = "Until tomorrow",
            isCalling = "is calling",
            urgent = "urgent",
            gotIt = "Got it",
            holdForUrgent = "Hold for an urgent call",
            quit = "Quit",
        )

        private val russian = Strings(
            yourName = "Ваше имя",
            contacts = "КОНТАКТЫ",
            nearby = "РЯДОМ",
            pairingRequests = "ЗАПРОСЫ",
            nobodyNearby = "Пока никого рядом. Откройте Earshot на другой машине.",
            online = "в сети",
            offline = "не в сети",
            pausedUntil = "на паузе",
            add = "Добавить",
            accept = "Принять",
            decline = "Отклонить",
            forget = "Удалить",
            pause = "Пауза",
            paused = "На паузе",
            resume = "Вернуться",
            minutes15 = "15 минут",
            hour1 = "1 час",
            untilTomorrow = "До завтра",
            isCalling = "зовёт",
            urgent = "срочно",
            gotIt = "Вижу",
            holdForUrgent = "Удерживайте для срочного вызова",
            quit = "Выйти",
        )

        fun current(): Strings =
            if (Locale.getDefault().language == "ru") russian else english
    }
}
