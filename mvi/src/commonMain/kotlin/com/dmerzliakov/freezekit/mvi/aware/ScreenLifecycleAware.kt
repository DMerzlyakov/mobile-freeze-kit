package com.dmerzliakov.freezekit.mvi.aware

/**
 * Получает события жизненного цикла экрана от UI.
 * Не отслеживает жизненный цикл автоматически и не управляет сроком жизни ViewModel.
 */
interface ScreenLifecycleAware {
    /** Вызывается при создании содержимого экрана */
    fun onScreenCreate() {}

    /** Вызывается, когда экран становится видимым */
    fun onScreenStart() {}

    /** Вызывается, когда экран получает фокус */
    fun onScreenResume() {}

    /** Вызывается, когда экран теряет фокус */
    fun onScreenPause() {}

    /** Вызывается, когда экран перестаёт быть видимым */
    fun onScreenStop() {}

    /** Вызывается при уничтожении содержимого экрана */
    fun onScreenDestroy() {}
}
