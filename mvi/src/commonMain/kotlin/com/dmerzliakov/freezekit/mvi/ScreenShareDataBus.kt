package com.dmerzliakov.freezekit.mvi

import com.dmerzliakov.freezekit.mvi.model.ScreenShareData
import com.dmerzliakov.freezekit.mvi.model.ScreenShareDataEnvelope
import com.dmerzliakov.freezekit.mvi.model.ScreenShareRequest
import com.dmerzliakov.freezekit.mvi.model.toEnvelope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Передаёт данные между экранами через единственный экземпляр шины на весь процесс.
 * Использует replay = 0 без дополнительного буфера: без подписчиков данные теряются,
 * а новые подписчики не получают прошлые данные. Не сохраняет данные при перезапуске процесса.
 */
@PublishedApi
internal object ScreenShareDataBus {
    /** Предоставляет данные активным подписчикам без повторной доставки */
    @PublishedApi
    internal val shareDataFlow: SharedFlow<ScreenShareDataEnvelope>



    private val _shareDataFlow = MutableSharedFlow<ScreenShareDataEnvelope>()



    init {
        shareDataFlow = _shareDataFlow.asSharedFlow()
    }



    /**
     * Передаёт [data] активным подписчикам запроса [request], ожидая их готовности.
     * Несколько подписчиков одного запроса могут получить одни и те же данные.
     *
     * @param request адресный запрос или запрос общего канала типа
     * @param data данные типа, связанного с запросом
     */
    internal suspend fun <T : ScreenShareData> emit(request: ScreenShareRequest<T>, data: T) =
        _shareDataFlow.emit(
            request.toEnvelope(data),
        )
}
