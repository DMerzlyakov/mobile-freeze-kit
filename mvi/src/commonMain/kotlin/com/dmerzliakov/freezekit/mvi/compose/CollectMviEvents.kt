package com.dmerzliakov.freezekit.mvi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.dmerzliakov.freezekit.mvi.model.ScreenEvent
import com.dmerzliakov.freezekit.mvi.viewmodel.MVIViewModel

/**
 * Передаёт события [viewModel] актуальному обработчику [onEvent], пока [lifecycleOwner] находится не ниже STARTED.
 * Остановка экрана отменяет подписку и обработчик, повторный запуск создаёт подписку заново.
 * Выход из composition или смена ViewModel либо владельца также отменяет подписку и обработчик.
 *
 * @param viewModel источник одноразовых событий экрана
 * @param lifecycleOwner владелец жизненного цикла экрана
 * @param onEvent обработчик события, обновляемый без перезапуска подписки
 */
@Composable
fun <Event : ScreenEvent> CollectMviEvents(
    viewModel: MVIViewModel<*, *, *, Event>,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onEvent: suspend (Event) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner
            .lifecycle
            .repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel
                    .uiEvent
                    .collect { currentOnEvent(it) }
            }
    }
}
