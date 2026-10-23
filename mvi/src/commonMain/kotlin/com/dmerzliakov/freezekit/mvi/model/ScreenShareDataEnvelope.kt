package com.dmerzliakov.freezekit.mvi.model

import kotlin.uuid.Uuid

/** Связывает данные общей шины с идентификатором запроса без изменения самих данных */
@PublishedApi
internal class ScreenShareDataEnvelope(
    /** Содержит идентификатор адресного запроса или null для общего канала типа */
    @PublishedApi internal val requestId: Uuid?,
    /** Содержит передаваемые данные экрана */
    @PublishedApi internal val data: ScreenShareData,
)
