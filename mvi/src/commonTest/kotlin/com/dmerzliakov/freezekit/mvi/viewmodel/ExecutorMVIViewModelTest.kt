package com.dmerzliakov.freezekit.mvi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.dmerzliakov.freezekit.mvi.executor.MVIExecutor
import com.dmerzliakov.freezekit.mvi.model.ScreenAction
import com.dmerzliakov.freezekit.mvi.model.ScreenBootstrapData
import com.dmerzliakov.freezekit.mvi.model.ScreenEvent
import com.dmerzliakov.freezekit.mvi.model.ScreenIntent
import com.dmerzliakov.freezekit.mvi.model.ScreenState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Проверяет [ExecutorMVIViewModel] и конфигурацию [MVIViewModel]
 *
 * Обработанные сценарии:
 * 1. [MVIViewModel.obtainIntent]
 *    1. [obtainIntentTest1] Проверяем, что сразу после создания типизированные Intent и Action обрабатываются
 *       одним executor, используют конфигурацию конструктора и заданное начальное
 *       состояние, а активный подписчик [MVIViewModel.uiEvent] получает ожидаемое событие
 *    2. [obtainIntentTest2] Проверяем, что изменения разных полей двумя executor сохраняются при пересечении
 *       обработчиков, а чтение состояния после приостановки возвращает актуальное значение
 *    3. [obtainIntentTest3] Проверяем, что Intent завершается при приостановленном Action, а Action меняет
 *       состояние и отправляет событие только после снятия приостановки
 *    4. [obtainIntentTest4] Проверяем, что отмена Job приостановленного Intent предотвращает изменение
 *       состояния, запуск Action и отправку события после снятия приостановки
 *    5. [obtainIntentTest5] Проверяем, что обычная [MVIViewModel] использует начальное состояние и
 *       конфигурацию конструктора при обработке Intent
 *
 * 2. [ViewModelStore.clear]
 *    1. [clearTest1] Проверяем, что очистка отменяет одновременно приостановленные Intent и Action,
 *       а снятие приостановки не приводит к последующим изменениям состояния или событиям
 *
 * 3. Конструктор [ExecutorMVIViewModel]
 *    1. [bindExecutorTest1] Проверяем, что создание второй ViewModel с привязанным executor отклоняется,
 *       а исходная ViewModel продолжает обрабатывать Intent со своей конфигурацией
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExecutorMVIViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()



    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        try {
            stores.forEach { it.clear() }
            dispatcher
                .scheduler
                .runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun obtainIntentTest1() = runTest(dispatcher) {
        val viewModel = createProbe()
        val intentWork = WorkGate(isReleased = true)
        val actionWork = WorkGate(isReleased = true)
        val events = mutableListOf<ProbeEvent>()
        backgroundScope.launch { viewModel.uiEvent.collect { events.add(it) } }
        runCurrent()

        val job = viewModel.obtainIntent(ProbeIntent(2, intentWork, actionWork))
        runCurrent()

        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
        assertTrue(intentWork.completed.isCompleted)
        assertTrue(actionWork.completed.isCompleted)
        assertEquals(ProbeState(first = 15, second = 38, observed = 15), viewModel.uiState.value)
        assertEquals(listOf(ProbeEvent(38)), events)
    }

    @Test
    fun obtainIntentTest2() = runTest(dispatcher) {
        val viewModel = createProbe(actionExecutor = ProbeExecutor())
        val suspendedAction = WorkGate(isReleased = false)
        val immediateAction = WorkGate(isReleased = true)
        viewModel.obtainIntent(ProbeIntent(2, WorkGate(isReleased = true), suspendedAction))
        runCurrent()

        assertTrue(suspendedAction.started.isCompleted)
        assertFalse(suspendedAction.completed.isCompleted)
        assertEquals(ProbeState(first = 15, second = 20, observed = 0), viewModel.uiState.value)

        viewModel.obtainIntent(ProbeIntent(2, WorkGate(isReleased = true), immediateAction))
        runCurrent()

        assertTrue(immediateAction.completed.isCompleted)
        assertEquals(ProbeState(first = 20, second = 43, observed = 20), viewModel.uiState.value)

        suspendedAction.release.complete(Unit)
        runCurrent()

        assertTrue(suspendedAction.completed.isCompleted)
        assertEquals(ProbeState(first = 20, second = 66, observed = 20), viewModel.uiState.value)
    }

    @Test
    fun obtainIntentTest3() = runTest(dispatcher) {
        val viewModel = createProbe()
        val actionWork = WorkGate(isReleased = false)
        val events = mutableListOf<ProbeEvent>()
        backgroundScope.launch { viewModel.uiEvent.collect { events.add(it) } }
        runCurrent()

        val job = viewModel.obtainIntent(ProbeIntent(2, WorkGate(isReleased = true), actionWork))
        runCurrent()

        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
        assertTrue(actionWork.started.isCompleted)
        assertFalse(actionWork.completed.isCompleted)
        assertEquals(ProbeState(first = 15, second = 20, observed = 0), viewModel.uiState.value)
        assertEquals(emptyList<ProbeEvent>(), events)

        actionWork.release.complete(Unit)
        runCurrent()

        assertTrue(actionWork.completed.isCompleted)
        assertEquals(ProbeState(first = 15, second = 38, observed = 15), viewModel.uiState.value)
        assertEquals(listOf(ProbeEvent(38)), events)
    }

    @Test
    fun obtainIntentTest4() = runTest(dispatcher) {
        val viewModel = createProbe()
        val intentWork = WorkGate(isReleased = false)
        val actionWork = WorkGate(isReleased = true)
        val events = mutableListOf<ProbeEvent>()
        backgroundScope.launch { viewModel.uiEvent.collect { events.add(it) } }
        runCurrent()

        val job = viewModel.obtainIntent(ProbeIntent(2, intentWork, actionWork))
        runCurrent()

        assertTrue(intentWork.started.isCompleted)
        assertTrue(job.isActive)

        job.cancel()
        runCurrent()
        intentWork.release.complete(Unit)
        runCurrent()

        assertTrue(job.isCancelled)
        assertTrue(intentWork.terminated.isCompleted)
        assertFalse(intentWork.completed.isCompleted)
        assertFalse(actionWork.started.isCompleted)
        assertEquals(ProbeState(first = 10, second = 20, observed = 0), viewModel.uiState.value)
        assertEquals(emptyList<ProbeEvent>(), events)
    }

    @Test
    fun obtainIntentTest5() = runTest(dispatcher) {
        val viewModel = PlainViewModel(
            initialState = ProbeState(first = 40, second = 50, observed = 60),
            bootstrapData = ProbeBootstrapData(increment = 7),
        )
        register(viewModel)

        val job = viewModel.obtainIntent(
            ProbeIntent(2, WorkGate(isReleased = true), WorkGate(isReleased = true)),
        )
        runCurrent()

        assertTrue(job.isCompleted)
        assertFalse(job.isCancelled)
        assertEquals(ProbeState(first = 49, second = 50, observed = 60), viewModel.uiState.value)
    }

    @Test
    fun clearTest1() = runTest(dispatcher) {
        val store = ViewModelStore()
        val viewModel = createProbe(store = store)
        val suspendedIntent = WorkGate(isReleased = false)
        val suspendedAction = WorkGate(isReleased = false)
        val unstartedAction = WorkGate(isReleased = true)
        val events = mutableListOf<ProbeEvent>()
        backgroundScope.launch { viewModel.uiEvent.collect { events.add(it) } }
        runCurrent()

        viewModel.obtainIntent(ProbeIntent(2, WorkGate(isReleased = true), suspendedAction))
        val intentJob = viewModel.obtainIntent(ProbeIntent(100, suspendedIntent, unstartedAction))
        runCurrent()

        assertTrue(suspendedIntent.started.isCompleted)
        assertTrue(suspendedAction.started.isCompleted)
        assertEquals(ProbeState(first = 15, second = 20, observed = 0), viewModel.uiState.value)

        store.clear()
        runCurrent()
        suspendedIntent.release.complete(Unit)
        suspendedAction.release.complete(Unit)
        runCurrent()

        assertTrue(intentJob.isCancelled)
        assertTrue(suspendedIntent.terminated.isCompleted)
        assertTrue(suspendedAction.terminated.isCompleted)
        assertFalse(suspendedIntent.completed.isCompleted)
        assertFalse(suspendedAction.completed.isCompleted)
        assertFalse(unstartedAction.started.isCompleted)
        assertEquals(ProbeState(first = 15, second = 20, observed = 0), viewModel.uiState.value)
        assertEquals(emptyList<ProbeEvent>(), events)
    }

    @Test
    fun bindExecutorTest1() = runTest(dispatcher) {
        val executor = ProbeExecutor()
        val first = createProbe(intentExecutor = executor)

        assertFailsWith<IllegalStateException> {
            createProbe(intentExecutor = executor, bootstrapData = ProbeBootstrapData(increment = 100))
        }

        first.obtainIntent(ProbeIntent(2, WorkGate(isReleased = true), WorkGate(isReleased = true)))
        runCurrent()

        assertEquals(ProbeState(first = 15, second = 38, observed = 15), first.uiState.value)
    }



    private fun createProbe(
        store: ViewModelStore = ViewModelStore(),
        intentExecutor: ProbeExecutor = ProbeExecutor(),
        actionExecutor: ProbeExecutor = intentExecutor,
        bootstrapData: ProbeBootstrapData = ProbeBootstrapData(increment = 3),
    ): ProbeViewModel {
        val viewModel = ProbeViewModel(
            initialState = ProbeState(first = 10, second = 20, observed = 0),
            bootstrapData = bootstrapData,
            intentExecutor = intentExecutor,
            actionExecutor = actionExecutor,
        )
        register(viewModel, store)

        return viewModel
    }

    private fun register(viewModel: ViewModel, store: ViewModelStore = ViewModelStore()) {
        stores.add(store)
        store.put("probe", viewModel)
    }



    private data class ProbeState(val first: Int, val second: Int, val observed: Int) : ScreenState

    private data class ProbeBootstrapData(val increment: Int) : ScreenBootstrapData

    private data class ProbeIntent(
        val amount: Int,
        val intentWork: WorkGate,
        val actionWork: WorkGate,
    ) : ScreenIntent

    private data class ProbeAction(val work: WorkGate) : ScreenAction

    private data class ProbeEvent(val value: Int) : ScreenEvent

    private class WorkGate(isReleased: Boolean) {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val terminated = CompletableDeferred<Unit>()



        init {
            if (isReleased) release.complete(Unit)
        }
    }

    private class ProbeViewModel(
        initialState: ProbeState,
        bootstrapData: ProbeBootstrapData,
        private val intentExecutor: ProbeExecutor,
        private val actionExecutor: ProbeExecutor,
    ) : ExecutorMVIViewModel<ProbeState, ProbeIntent, ProbeAction, ProbeEvent, ProbeBootstrapData>(
        initialState = initialState,
        bootstrapData = bootstrapData,
        executors = if (intentExecutor === actionExecutor) listOf(intentExecutor) else {
            listOf(intentExecutor, actionExecutor)
        },
    ) {
        override suspend fun executeIntent(intent: ProbeIntent) = intentExecutor.executeIntent(intent)

        override suspend fun executeAction(action: ProbeAction) = actionExecutor.executeAction(action)
    }

    private class ProbeExecutor : MVIExecutor<
        ProbeState, ProbeAction, ProbeEvent, ProbeBootstrapData,
    >() {
        suspend fun executeIntent(intent: ProbeIntent) = add(intent)

        suspend fun executeAction(action: ProbeAction) = calculate(action)



        private suspend fun add(intent: ProbeIntent) {
            withWork(intent.intentWork) {
                updateState { it.copy(first = it.first + intent.amount + bootstrapData.increment) }

                obtainAction(ProbeAction(intent.actionWork))
            }
        }

        private suspend fun calculate(action: ProbeAction) {
            withWork(action.work) {
                val currentFirst = uiState.first

                updateState {
                    it.copy(second = it.second + currentFirst + bootstrapData.increment, observed = currentFirst)
                }

                sendEvent(ProbeEvent(uiState.second))
            }
        }

        private suspend fun withWork(work: WorkGate, block: suspend () -> Unit) {
            work.started.complete(Unit)

            try {
                work.release.await()

                block()
                work.completed.complete(Unit)
            } finally {
                work.terminated.complete(Unit)
            }
        }
    }

    private class PlainViewModel(
        initialState: ProbeState,
        bootstrapData: ProbeBootstrapData,
    ) : MVIViewModel<ProbeState, ProbeIntent, ProbeAction, ProbeEvent, ProbeBootstrapData>(
        initialState = initialState,
        bootstrapData = bootstrapData,
    ) {
        override suspend fun executeIntent(intent: ProbeIntent) = add(intent.amount)

        override suspend fun executeAction(action: ProbeAction) = Unit



        private fun add(amount: Int) {
            updateState { it.copy(first = it.first + amount + bootstrapData.increment) }
        }
    }
}
