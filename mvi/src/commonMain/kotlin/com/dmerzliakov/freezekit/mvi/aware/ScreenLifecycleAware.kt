package com.dmerzliakov.freezekit.mvi.aware

import com.dmerzliakov.freezekit.mvi.model.LifecycleScreenIntent
import kotlinx.coroutines.Job

/**
 * Получает события жизненного цикла экрана от UI.
 * Не отслеживает жизненный цикл автоматически и не управляет сроком жизни ViewModel.
 */
interface ScreenLifecycleAware {
    /**
     * Запускает асинхронную обработку события жизненного цикла [intent]
     *
     * @param intent событие composition или жизненного цикла владельца экрана
     * @return задача обработки события
     */
    fun obtainLifecycleIntent(intent: LifecycleScreenIntent): Job
}
