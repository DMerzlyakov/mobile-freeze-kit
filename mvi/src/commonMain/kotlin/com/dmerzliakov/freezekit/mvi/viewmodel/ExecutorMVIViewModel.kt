package com.dmerzliakov.freezekit.mvi.viewmodel

import com.dmerzliakov.freezekit.mvi.executor.ExecutorScope
import com.dmerzliakov.freezekit.mvi.executor.MVIExecutor
import com.dmerzliakov.freezekit.mvi.model.ScreenAction
import com.dmerzliakov.freezekit.mvi.model.ScreenBootstrapData
import com.dmerzliakov.freezekit.mvi.model.ScreenEvent
import com.dmerzliakov.freezekit.mvi.model.ScreenIntent
import com.dmerzliakov.freezekit.mvi.model.ScreenState

/**
 * Привязывает [executors] к общему состоянию и конфигурации [bootstrapData] экрана.
 * Наследник явно маршрутизирует намерения и действия через обработчики [MVIViewModel].
 * Каждый executor создаётся отдельно для одной ViewModel; привязка не запускает обработку.
 *
 * @param initialState начальное состояние экрана
 * @param bootstrapData неизменяемая конфигурация экрана
 * @param executors обработчики, привязываемые последовательно после создания базовой ViewModel
 */
abstract class ExecutorMVIViewModel<
    State : ScreenState,
    Intent : ScreenIntent,
    Action : ScreenAction,
    Event : ScreenEvent,
    BootstrapData : ScreenBootstrapData,
>(
    initialState: State,
    bootstrapData: BootstrapData,
    executors: List<MVIExecutor<State, Action, Event, BootstrapData>>,
) : MVIViewModel<State, Intent, Action, Event, BootstrapData>(initialState, bootstrapData) {
    init {
        val scope = object : ExecutorScope<State, Action, Event> {
            override val uiState: State
                get() = this@ExecutorMVIViewModel.uiState.value



            override fun updateState(function: (State) -> State) {
                this@ExecutorMVIViewModel.updateState(function)
            }

            override suspend fun sendEvent(event: Event) {
                this@ExecutorMVIViewModel.sendEvent(event)
            }

            override fun obtainAction(action: Action) {
                this@ExecutorMVIViewModel.obtainAction(action)
            }
        }

        executors.forEach { it.bind(scope = scope, bootstrapData = this.bootstrapData) }
    }
}
