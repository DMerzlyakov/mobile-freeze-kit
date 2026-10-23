package com.dmerzliakov.freezekit.mvi.model

import kotlin.uuid.Uuid

/**
 * Связывает отправителя и получателя данных типа [T] без хранения результата.
 * Для каждого открытия экрана создаётся отдельный запрос через [create] и передаётся целиком через навигацию.
 * Получатель подписывается до открытия экрана и отправки данных; без подписчика данные теряются.
 * Запрос [broadcast] объединяет подписчиков одного типа без адресации отдельному открытию экрана.
 */
class ScreenShareRequest<T : ScreenShareData> private constructor(
    /** Содержит идентификатор адресного запроса или null для общего канала типа */
    @PublishedApi internal val id: Uuid?,
) {
    /** Создаёт адресный запрос или запрос общего канала типа */
    companion object {
        /** Создаёт уникальный запрос для отдельного открытия экрана и результата типа [T] */
        fun <T : ScreenShareData> create(): ScreenShareRequest<T> = ScreenShareRequest(id = Uuid.random())

        /**
         * Создаёт запрос общего канала типа [T] без идентификатора.
         * Независимые экземпляры принимают одни и те же данные подходящего типа;
         * адресные запросы не получают эти данные, а общий канал не получает адресные результаты.
         */
        fun <T : ScreenShareData> broadcast(): ScreenShareRequest<T> = ScreenShareRequest(id = null)
    }
}
