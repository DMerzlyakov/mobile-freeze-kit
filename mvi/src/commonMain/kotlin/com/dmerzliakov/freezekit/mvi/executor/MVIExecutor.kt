package com.dmerzliakov.freezekit.mvi.executor

import com.dmerzliakov.freezekit.mvi.model.ScreenAction
import com.dmerzliakov.freezekit.mvi.model.ScreenBootstrapData
import com.dmerzliakov.freezekit.mvi.model.ScreenEvent
import com.dmerzliakov.freezekit.mvi.model.ScreenState

/**
 * Предоставляет обработчику состояние, конфигурацию и сообщения одного экрана.
 * Наследник объединяет обработку намерений и действий своей области в типизированных suspend-методах
 * executeIntent и executeAction, переиспользуя общие private-методы.
 * ViewModel явно вызывает эти методы в корутине своего обработчика.
 * Экземпляр привязывается однократно при последовательном создании ViewModel и не переиспользуется.
 * Привязка сохраняет зависимости без запуска обработчиков или переопределяемых методов.
 */
abstract class MVIExecutor<
    State : ScreenState,
    Action : ScreenAction,
    Event : ScreenEvent,
    BootstrapData : ScreenBootstrapData,
> {
    /** Предоставляет актуальное состояние общего экрана, в том числе после приостановки обработчика */
    protected val uiState: State
        get() = executorScope.uiState

    /** Предоставляет неизменяемую конфигурацию экрана после привязки */
    protected val bootstrapData: BootstrapData
        get() = _bootstrapData



    private lateinit var executorScope: ExecutorScope<State, Action, Event>
    private lateinit var _bootstrapData: BootstrapData



    /**
     * Атомарно обновляет общее состояние преобразованием [function]
     *
     * @param function преобразование актуального состояния без побочных эффектов
     */
    protected fun updateState(function: (State) -> State) = executorScope.updateState(function)

    /**
     * Отправляет одноразовое событие [event] подписчикам экрана
     *
     * @param event одноразовое событие экрана
     */
    protected suspend fun sendEvent(event: Event) = executorScope.sendEvent(event)

    /**
     * Запускает обработку [action] без ожидания её завершения
     *
     * @param action внутреннее действие экрана
     */
    protected fun obtainAction(action: Action) = executorScope.obtainAction(action)

    /**
     * Однократно привязывает [scope] и [bootstrapData] без выполнения обработчиков
     *
     * @param scope доступ к общему состоянию и сообщениям экрана
     * @param bootstrapData неизменяемая конфигурация экрана
     * @throws IllegalStateException если экземпляр уже привязан к ViewModel
     */
    internal fun bind(scope: ExecutorScope<State, Action, Event>, bootstrapData: BootstrapData) {
        check(!::executorScope.isInitialized) {
            "Executor is already bound to a ViewModel. Create a new executor instance for each ViewModel."
        }

        _bootstrapData = bootstrapData
        executorScope = scope
    }
}
