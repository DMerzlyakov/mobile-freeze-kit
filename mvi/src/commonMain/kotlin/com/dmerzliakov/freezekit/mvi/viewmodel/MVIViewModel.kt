package com.dmerzliakov.freezekit.mvi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmerzliakov.freezekit.mvi.ScreenShareDataBus
import com.dmerzliakov.freezekit.mvi.aware.ScreenLifecycleAware
import com.dmerzliakov.freezekit.mvi.model.LifecycleScreenIntent
import com.dmerzliakov.freezekit.mvi.model.ScreenAction
import com.dmerzliakov.freezekit.mvi.model.ScreenBootstrapData
import com.dmerzliakov.freezekit.mvi.model.ScreenEvent
import com.dmerzliakov.freezekit.mvi.model.ScreenIntent
import com.dmerzliakov.freezekit.mvi.model.ScreenShareData
import com.dmerzliakov.freezekit.mvi.model.ScreenShareRequest
import com.dmerzliakov.freezekit.mvi.model.ScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Управляет состоянием [State], намерениями [Intent], действиями [Action] и событиями [Event] MVI-экрана.
 * Использует [initialState] как начальное состояние.
 * Выполняет обработчики и обмен данными в [viewModelScope] без перехвата ошибок.
 * Очистка [ViewModel] отменяет обработчики и подписки. События жизненного цикла передаёт UI.
 *
 * @param initialState начальное состояние экрана
 * @param bootstrapData неизменяемая конфигурация экрана, доступная через [bootstrapData]
 */
abstract class MVIViewModel<
    State : ScreenState,
    Intent : ScreenIntent,
    Action : ScreenAction,
    Event : ScreenEvent,
    BootstrapData : ScreenBootstrapData,
>(
    initialState: State,
    /** Предоставляет конфигурацию экрана отдельно от изменяемого [uiState] */
    protected val bootstrapData: BootstrapData,
) : ViewModel(), ScreenLifecycleAware {
    /** Предоставляет текущее состояние экрана */
    val uiState: StateFlow<State>

    /** Предоставляет события без повторной доставки и вызывает [onScreenSubscribeToEvent] при каждой подписке */
    val uiEvent: SharedFlow<Event>



    private val _uiState = MutableStateFlow(initialState)
    private val _uiEvent = MutableSharedFlow<Event>()



    init {
        uiState = _uiState.asStateFlow()
        uiEvent = _uiEvent
            .asSharedFlow()
            .onSubscription { onScreenSubscribeToEvent() }
    }



    final override fun obtainLifecycleIntent(intent: LifecycleScreenIntent): Job = viewModelScope.launch {
        executeLifecycleIntent(intent)
    }



    /**
     * Обрабатывает намерение пользователя [intent]
     *
     * @param intent намерение пользователя
     */
    protected abstract suspend fun executeIntent(intent: Intent)

    /**
     * Обрабатывает внутреннее действие [action]
     *
     * @param action внутреннее действие экрана
     */
    protected abstract suspend fun executeAction(action: Action)



    /**
     * Обрабатывает событие жизненного цикла [intent] в [viewModelScope].
     * Каждый вызов запускается в отдельной корутине без ожидания завершения предыдущего.
     * Очистка [ViewModel] отменяет обработку, поэтому [LifecycleScreenIntent.LeaveComposition]
     * не гарантирует освобождение ресурсов.
     *
     * @param intent событие composition или жизненного цикла владельца экрана
     */
    protected open suspend fun executeLifecycleIntent(intent: LifecycleScreenIntent) {}

    /** Выполняет дополнительную логику при каждой подписке на [uiEvent] */
    protected open suspend fun onScreenSubscribeToEvent() {}



    /**
     * Запускает обработку намерения [intent] в [viewModelScope]
     *
     * @param intent намерение пользователя
     */
    fun obtainIntent(intent: Intent): Job = viewModelScope.launch { executeIntent(intent) }

    /**
     * Запускает обработку действия [action] в [viewModelScope]
     *
     * @param action внутреннее действие экрана
     */
    protected fun obtainAction(action: Action): Job = viewModelScope.launch { executeAction(action) }

    /**
     * Атомарно обновляет состояние экрана преобразованием [function]
     *
     * @param function преобразование текущего состояния в новое
     */
    protected fun updateState(function: (State) -> State) = _uiState.update(function)

    /**
     * Отправляет одноразовое событие [event] подписчикам [uiEvent]
     *
     * @param event одноразовое событие экрана
     */
    protected suspend fun sendEvent(event: Event) = _uiEvent.emit(event)

    /**
     * Отправляет [data] активным подписчикам запроса [request] в [viewModelScope].
     * Без подписчиков данные теряются и новым подписчикам не доставляются.
     *
     * @param request адресный запрос или запрос общего канала типа
     * @param data данные типа, связанного с запросом
     */
    protected fun <T : ScreenShareData> shareData(
        request: ScreenShareRequest<T>,
        data: T,
    ): Job = viewModelScope.launch {
        ScreenShareDataBus.emit(request = request, data = data)
    }

    /**
     * Ожидает первый объект типа [T] для запроса [request] и передаёт его в [onReceive].
     * Подписка и обработчик выполняются в [viewModelScope] и отменяются при очистке [ViewModel].
     * Ранее отправленные данные не доставляются; подписка не привязана к видимости экрана.
     * Получатель подписывается до открытия экрана и отправки данных.
     * Несколько подписчиков одного запроса могут получить один результат.
     *
     * @param request адресный запрос или запрос общего канала типа
     * @param onReceive обработчик первого подходящего результата
     */
    protected inline fun <reified T : ScreenShareData> setShareDataListener(
        request: ScreenShareRequest<T>,
        noinline onReceive: suspend (T) -> Unit,
    ): Job = viewModelScope.launch {
        val data = ScreenShareDataBus
            .shareDataFlow
            .filter { it.requestId == request.id }
            .map { it.data }
            .filterIsInstance<T>()
            .first()

        onReceive(data)
    }

    /**
     * Передаёт каждый полученный объект типа [T] для запроса [request] в [onReceive].
     * Подписка и обработчик выполняются в [viewModelScope] и отменяются при очистке [ViewModel].
     * Ранее отправленные данные не доставляются; подписка не привязана к видимости экрана.
     * Получатель подписывается до открытия экрана и отправки данных.
     * Несколько подписчиков одного запроса могут получить одни и те же данные.
     *
     * @param request адресный запрос или запрос общего канала типа
     * @param onReceive обработчик каждого подходящего результата
     */
    protected inline fun <reified T : ScreenShareData> collectShareData(
        request: ScreenShareRequest<T>,
        noinline onReceive: suspend (T) -> Unit,
    ): Job = viewModelScope.launch {
        ScreenShareDataBus
            .shareDataFlow
            .filter { it.requestId == request.id }
            .map { it.data }
            .filterIsInstance<T>()
            .collect { onReceive(it) }
    }
}
