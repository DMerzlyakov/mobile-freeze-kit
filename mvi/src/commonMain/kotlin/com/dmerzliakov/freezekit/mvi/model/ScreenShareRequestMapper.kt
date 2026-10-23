package com.dmerzliakov.freezekit.mvi.model

/**
 * Связывает запрос с передаваемыми данными [data] в сообщении общей шины
 *
 * @param data данные типа, связанного с запросом
 */
internal fun <T : ScreenShareData> ScreenShareRequest<T>.toEnvelope(data: T): ScreenShareDataEnvelope =
    ScreenShareDataEnvelope(requestId = id, data = data)
