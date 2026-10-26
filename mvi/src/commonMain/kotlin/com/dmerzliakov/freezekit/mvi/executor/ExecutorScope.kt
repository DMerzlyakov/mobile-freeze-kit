package com.dmerzliakov.freezekit.mvi.executor

import com.dmerzliakov.freezekit.mvi.model.ScreenAction
import com.dmerzliakov.freezekit.mvi.model.ScreenEvent
import com.dmerzliakov.freezekit.mvi.model.ScreenState

/** Предоставляет executor доступ к общему состоянию и сообщениям экрана без ссылки на ViewModel */
interface ExecutorScope<State : ScreenState, Action : ScreenAction, Event : ScreenEvent> {
    /** Предоставляет актуальное состояние экрана при каждом чтении */
    val uiState: State



    /**
     * Атомарно обновляет состояние преобразованием [function], которое может вызываться повторно
     *
     * @param function преобразование актуального состояния без побочных эффектов
     */
    fun updateState(function: (State) -> State)

    /**
     * Отправляет одноразовое событие [event] активным подписчикам
     *
     * @param event одноразовое событие экрана
     */
    suspend fun sendEvent(event: Event)

    /**
     * Запускает обработку [action] в scope ViewModel без ожидания её завершения
     *
     * @param action внутреннее действие экрана
     */
    fun obtainAction(action: Action)
}
