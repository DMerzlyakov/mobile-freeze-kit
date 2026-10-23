package com.dmerzliakov.freezekit

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Запускает пустое окно шаблонного Desktop-приложения */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Mobilefreezekit",
    ) {}
}
