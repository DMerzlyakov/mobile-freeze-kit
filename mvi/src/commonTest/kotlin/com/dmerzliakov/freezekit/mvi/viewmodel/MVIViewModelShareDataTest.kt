package com.dmerzliakov.freezekit.mvi.viewmodel

import androidx.lifecycle.ViewModelStore
import com.dmerzliakov.freezekit.mvi.model.ScreenAction
import com.dmerzliakov.freezekit.mvi.model.ScreenBootstrapData
import com.dmerzliakov.freezekit.mvi.model.ScreenEvent
import com.dmerzliakov.freezekit.mvi.model.ScreenIntent
import com.dmerzliakov.freezekit.mvi.model.ScreenShareData
import com.dmerzliakov.freezekit.mvi.model.ScreenShareRequest
import com.dmerzliakov.freezekit.mvi.model.ScreenState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Проверяет [MVIViewModel] при обмене данными через [ScreenShareRequest]
 *
 * Protected-методы вызываются обработчиками намерений тестовой [ProbeViewModel],
 * все намерения передаются через [MVIViewModel.obtainIntent].
 * [ProbeIntent.Listen] с `isFirst = true` использует [MVIViewModel.setShareDataListener],
 * с `isFirst = false` и [ProbeIntent.ListenNumber] — [MVIViewModel.collectShareData],
 * [ProbeIntent.Send] и [ProbeIntent.SendNumber] — [MVIViewModel.shareData].
 *
 * Обработанные сценарии:
 * 1. [MVIViewModel.setShareDataListener]
 *    1. [setShareDataListenerTest1] Проверяем, что после [ProbeIntent.Listen] с `isFirst = true`
 *       и адресным запросом `request` получатель игнорирует [ProbeIntent.Send] с другим запросом:
 *       список `received` пуст, Job активен;
 *       после Send с `request` и значениями `first`, `second` список содержит только `first`,
 *       а Job завершён без отмены
 *
 * 2. [MVIViewModel.collectShareData]: [ProbeIntent.Listen] с `isFirst = false` и [ProbeIntent.ListenNumber]
 *    1. [collectShareDataTest1] Проверяем, что два получателя с Listen для разных адресных запросов
 *       типа [TextData] после [ProbeIntent.Send] со значениями `first` и `second` для соответствующих
 *       запросов имеют в `received` только собственное значение
 *    2. [collectShareDataTest2] Проверяем, что после Listen и последовательных [ProbeIntent.Send]
 *       с одним адресным запросом и значениями `first`, `second`, `third` список `received` содержит
 *       все три значения в этом порядке, а Job подписки остаётся активным
 *    3. [collectShareDataTest3] Проверяем, что [ProbeIntent.Send] со значением `before` до Listen
 *       не попадает в `received`, Send со значением `active` после Listen попадает;
 *       после [ProbeIntent.Cancel], Send со значением `gap` и повторного Listen список сохраняет
 *       только `active`, а следующий Send со значением `resubscribed` добавляет его в конец списка
 *    4. [collectShareDataTest4] Проверяем, что два получателя с Listen для независимо созданных
 *       [ScreenShareRequest.broadcast] типа [TextData] после [ProbeIntent.Send] с общим запросом
 *       и значением `broadcast` оба имеют это значение в `received`
 *    5. [collectShareDataTest5] Проверяем, что получатели с Listen для адресного запроса и
 *       [ScreenShareRequest.broadcast] типа [TextData] после [ProbeIntent.Send] с адресным запросом
 *       и значением `addressed` имеют списки `received` из `addressed` и пустой соответственно,
 *       а после Send с общим запросом и значением `broadcast` — из `addressed` и `broadcast` соответственно
 *    6. [collectShareDataTest6] Проверяем, что получатели с ListenNumber для [NumberData] и Listen для
 *       [TextData] в [ScreenShareRequest.broadcast] после [ProbeIntent.SendNumber] со значением `7`
 *       имеют в `received` строку `7` и пустой список соответственно, а текстовая подписка активна;
 *       после [ProbeIntent.Send] со значением `text` текстовый список содержит `text`, числовой сохраняет `7`
 *
 * 3. [ViewModelStore.clear]: внешнее воздействие жизненного цикла
 *    1. [clearTest1] Проверяем, что очистка ViewModel после [ProbeIntent.Listen] с `isFirst = true`
 *       для отдельного запроса отменяет Job ожидания и оставляет `received` пустым;
 *       другая ViewModel после [ProbeIntent.ListenSuspended] и [ProbeIntent.Send] с `request` и `first`
 *       имеет активный Job, `received` из `first` и пустой `completed`;
 *       её очистка отменяет Job и после снятия приостановки и Send с `request` и `second` сохраняет
 *       эти списки, а третья ViewModel с Listen(`request`, `isFirst = false`) остаётся активной
 *       и получает в `received` оба значения `first`, `second`
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MVIViewModelShareDataTest {
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
            dispatcher.scheduler.runCurrent()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun collectShareDataTest1() = runTest(dispatcher) {
        val firstRequest = ScreenShareRequest.create<TextData>()
        val secondRequest = ScreenShareRequest.create<TextData>()
        val first = createProbe()
        val second = createProbe()
        val sender = createProbe()
        dispatch(first, ProbeIntent.Listen(firstRequest, isFirst = false))
        dispatch(second, ProbeIntent.Listen(secondRequest, isFirst = false))

        dispatch(sender, ProbeIntent.Send(firstRequest, TextData("first")))
        dispatch(sender, ProbeIntent.Send(secondRequest, TextData("second")))

        assertEquals(listOf("first"), first.uiState.value.received)
        assertEquals(listOf("second"), second.uiState.value.received)
    }

    @Test
    fun setShareDataListenerTest1() = runTest(dispatcher) {
        val request = ScreenShareRequest.create<TextData>()
        val receiver = createProbe()
        val sender = createProbe()
        dispatch(receiver, ProbeIntent.Listen(request, isFirst = true))

        dispatch(sender, ProbeIntent.Send(ScreenShareRequest.create(), TextData("foreign")))

        assertTrue(receiver.listenerJob.isActive)
        assertEquals(emptyList<String>(), receiver.uiState.value.received)

        dispatch(sender, ProbeIntent.Send(request, TextData("first")))
        dispatch(sender, ProbeIntent.Send(request, TextData("second")))

        assertTrue(receiver.listenerJob.isCompleted)
        assertFalse(receiver.listenerJob.isCancelled)
        assertEquals(listOf("first"), receiver.uiState.value.received)
    }

    @Test
    fun collectShareDataTest2() = runTest(dispatcher) {
        val request = ScreenShareRequest.create<TextData>()
        val receiver = createProbe()
        val sender = createProbe()
        dispatch(receiver, ProbeIntent.Listen(request, isFirst = false))

        listOf("first", "second", "third").forEach { value ->
            dispatch(sender, ProbeIntent.Send(request, TextData(value)))
        }

        assertEquals(listOf("first", "second", "third"), receiver.uiState.value.received)
        assertTrue(receiver.listenerJob.isActive)
    }

    @Test
    fun collectShareDataTest3() = runTest(dispatcher) {
        val request = ScreenShareRequest.create<TextData>()
        val receiver = createProbe()
        val sender = createProbe()
        dispatch(sender, ProbeIntent.Send(request, TextData("before")))
        dispatch(receiver, ProbeIntent.Listen(request, isFirst = false))

        assertEquals(emptyList<String>(), receiver.uiState.value.received)

        dispatch(sender, ProbeIntent.Send(request, TextData("active")))
        dispatch(receiver, ProbeIntent.Cancel)
        dispatch(sender, ProbeIntent.Send(request, TextData("gap")))
        dispatch(receiver, ProbeIntent.Listen(request, isFirst = false))

        assertEquals(listOf("active"), receiver.uiState.value.received)

        dispatch(sender, ProbeIntent.Send(request, TextData("resubscribed")))

        assertEquals(listOf("active", "resubscribed"), receiver.uiState.value.received)
    }

    @Test
    fun clearTest1() = runTest(dispatcher) {
        val request = ScreenShareRequest.create<TextData>()
        val waitingStore = ViewModelStore()
        val suspendedStore = ViewModelStore()
        val waiting = createProbe(waitingStore)
        val suspended = createProbe(suspendedStore)
        val survivor = createProbe()
        val sender = createProbe()
        val gate = CompletableDeferred<Unit>()
        dispatch(waiting, ProbeIntent.Listen(ScreenShareRequest.create(), isFirst = true))
        dispatch(suspended, ProbeIntent.ListenSuspended(request, gate))
        dispatch(survivor, ProbeIntent.Listen(request, isFirst = false))

        waitingStore.clear()
        dispatch(sender, ProbeIntent.Send(request, TextData("first")))

        assertTrue(waiting.listenerJob.isCancelled)
        assertEquals(emptyList<String>(), waiting.uiState.value.received)
        assertTrue(suspended.listenerJob.isActive)
        assertEquals(listOf("first"), suspended.uiState.value.received)
        assertEquals(emptyList<String>(), suspended.uiState.value.completed)

        suspendedStore.clear()
        runCurrent()
        gate.complete(Unit)
        dispatch(sender, ProbeIntent.Send(request, TextData("second")))

        assertTrue(suspended.listenerJob.isCancelled)
        assertEquals(emptyList<String>(), suspended.uiState.value.completed)
        assertEquals(listOf("first"), suspended.uiState.value.received)
        assertTrue(survivor.listenerJob.isActive)
        assertEquals(listOf("first", "second"), survivor.uiState.value.received)
    }

    @Test
    fun collectShareDataTest4() = runTest(dispatcher) {
        val first = createProbe()
        val second = createProbe()
        val sender = createProbe()
        dispatch(first, ProbeIntent.Listen(ScreenShareRequest.broadcast(), isFirst = false))
        dispatch(second, ProbeIntent.Listen(ScreenShareRequest.broadcast(), isFirst = false))

        dispatch(sender, ProbeIntent.Send(ScreenShareRequest.broadcast(), TextData("broadcast")))

        assertEquals(listOf("broadcast"), first.uiState.value.received)
        assertEquals(listOf("broadcast"), second.uiState.value.received)
    }

    @Test
    fun collectShareDataTest6() = runTest(dispatcher) {
        val textReceiver = createProbe()
        val numberReceiver = createProbe()
        val sender = createProbe()
        dispatch(textReceiver, ProbeIntent.Listen(ScreenShareRequest.broadcast(), isFirst = false))
        dispatch(numberReceiver, ProbeIntent.ListenNumber(ScreenShareRequest.broadcast()))

        dispatch(sender, ProbeIntent.SendNumber(ScreenShareRequest.broadcast(), NumberData(7)))

        assertEquals(emptyList<String>(), textReceiver.uiState.value.received)
        assertEquals(listOf("7"), numberReceiver.uiState.value.received)
        assertTrue(textReceiver.listenerJob.isActive)

        dispatch(sender, ProbeIntent.Send(ScreenShareRequest.broadcast(), TextData("text")))

        assertEquals(listOf("text"), textReceiver.uiState.value.received)
        assertEquals(listOf("7"), numberReceiver.uiState.value.received)
    }

    @Test
    fun collectShareDataTest5() = runTest(dispatcher) {
        val request = ScreenShareRequest.create<TextData>()
        val addressed = createProbe()
        val broadcast = createProbe()
        val sender = createProbe()
        dispatch(addressed, ProbeIntent.Listen(request, isFirst = false))
        dispatch(broadcast, ProbeIntent.Listen(ScreenShareRequest.broadcast(), isFirst = false))

        dispatch(sender, ProbeIntent.Send(request, TextData("addressed")))

        assertEquals(listOf("addressed"), addressed.uiState.value.received)
        assertEquals(emptyList<String>(), broadcast.uiState.value.received)

        dispatch(sender, ProbeIntent.Send(ScreenShareRequest.broadcast(), TextData("broadcast")))

        assertEquals(listOf("addressed"), addressed.uiState.value.received)
        assertEquals(listOf("broadcast"), broadcast.uiState.value.received)
    }



    private fun createProbe(store: ViewModelStore = ViewModelStore()): ProbeViewModel {
        val viewModel = ProbeViewModel()
        stores.add(store)
        store.put("probe", viewModel)

        return viewModel
    }

    private suspend fun TestScope.dispatch(viewModel: ProbeViewModel, intent: ProbeIntent) {
        viewModel
            .obtainIntent(intent)
            .join()
        runCurrent()
    }



    private data class TextData(val value: String) : ScreenShareData

    private data class NumberData(val value: Int) : ScreenShareData

    private data class ProbeState(val received: List<String>, val completed: List<String>) : ScreenState

    private sealed interface ProbeIntent : ScreenIntent {
        data class Listen(val request: ScreenShareRequest<TextData>, val isFirst: Boolean) : ProbeIntent

        data class ListenSuspended(
            val request: ScreenShareRequest<TextData>,
            val gate: CompletableDeferred<Unit>,
        ) : ProbeIntent

        data class ListenNumber(val request: ScreenShareRequest<NumberData>) : ProbeIntent

        data class Send(val request: ScreenShareRequest<TextData>, val data: TextData) : ProbeIntent

        data class SendNumber(val request: ScreenShareRequest<NumberData>, val data: NumberData) : ProbeIntent

        data object Cancel : ProbeIntent
    }

    private data object ProbeBootstrapData : ScreenBootstrapData

    private class ProbeViewModel : MVIViewModel<
        ProbeState, ProbeIntent, ScreenAction, ScreenEvent, ProbeBootstrapData,
    >(
        initialState = ProbeState(received = emptyList(), completed = emptyList()),
        bootstrapData = ProbeBootstrapData,
    ) {
        lateinit var listenerJob: Job
            private set



        override suspend fun executeIntent(intent: ProbeIntent) {
            when (intent) {
                is ProbeIntent.Listen -> {
                    listenerJob = if (intent.isFirst) {
                        setShareDataListener(intent.request) { receive(it.value) }
                    } else {
                        collectShareData(intent.request) { receive(it.value) }
                    }
                }

                is ProbeIntent.ListenSuspended -> {
                    listenerJob = setShareDataListener(intent.request) {
                        receive(it.value)

                        intent.gate.await()

                        updateState { state -> state.copy(completed = state.completed + it.value) }
                    }
                }

                is ProbeIntent.ListenNumber -> {
                    listenerJob = collectShareData(intent.request) { receive(it.value.toString()) }
                }

                is ProbeIntent.Send -> shareData(intent.request, intent.data)
                    .join()
                is ProbeIntent.SendNumber -> shareData(intent.request, intent.data)
                    .join()
                ProbeIntent.Cancel -> listenerJob.cancel()
            }
        }

        override suspend fun executeAction(action: ScreenAction) = Unit



        private fun receive(value: String) {
            updateState { it.copy(received = it.received + value) }
        }
    }
}
