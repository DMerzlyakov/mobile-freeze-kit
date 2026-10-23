package com.dmerzliakov.freezekit.mvi.model

/** Отделяет события composition и жизненного цикла владельца экрана от пользовательских намерений */
sealed interface LifecycleScreenIntent : ScreenIntent {
    /** Обозначает вход в composition или установку effect с новым получателем либо владельцем */
    data object EnterComposition : LifecycleScreenIntent

    /**
     * Обозначает выход из composition или очистку effect при смене получателя либо владельца.
     * Не гарантирует выполнение очистки ресурсов: задача обработки может быть отменена вместе со scope.
     */
    data object LeaveComposition : LifecycleScreenIntent

    /** Обозначает запуск владельца жизненного цикла экрана */
    data object Start : LifecycleScreenIntent

    /** Обозначает остановку владельца жизненного цикла экрана или очистку соответствующего effect */
    data object Stop : LifecycleScreenIntent

    /** Обозначает возобновление владельца жизненного цикла экрана */
    data object Resume : LifecycleScreenIntent

    /** Обозначает паузу владельца жизненного цикла экрана или очистку соответствующего effect */
    data object Pause : LifecycleScreenIntent
}
