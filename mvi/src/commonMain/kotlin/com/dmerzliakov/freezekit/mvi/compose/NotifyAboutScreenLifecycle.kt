package com.dmerzliakov.freezekit.mvi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmerzliakov.freezekit.mvi.aware.ScreenLifecycleAware
import com.dmerzliakov.freezekit.mvi.model.LifecycleScreenIntent

/**
 * Передаёт события жизненного цикла экрана получателю [aware].
 * [LifecycleScreenIntent.EnterComposition] и [LifecycleScreenIntent.LeaveComposition] соответствуют входу в composition
 * и выходу из неё, а также смене получателя или владельца.
 * Запуск, остановка, возобновление и пауза соответствуют жизненному циклу [lifecycleOwner] и очистке effects.
 * Отправка события не ожидает завершения обработки.
 * [LifecycleScreenIntent.LeaveComposition] не гарантирует очистку ресурсов.
 *
 * @param aware получатель событий жизненного цикла экрана
 * @param lifecycleOwner владелец жизненного цикла экрана
 */
@Composable
fun NotifyAboutScreenLifecycle(
    aware: ScreenLifecycleAware,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    DisposableEffect(aware, lifecycleOwner) {
        aware.obtainLifecycleIntent(LifecycleScreenIntent.EnterComposition)

        onDispose {
            aware.obtainLifecycleIntent(LifecycleScreenIntent.LeaveComposition)
        }
    }

    LifecycleStartEffect(aware, lifecycleOwner, lifecycleOwner = lifecycleOwner) {
        aware.obtainLifecycleIntent(LifecycleScreenIntent.Start)
        onStopOrDispose { aware.obtainLifecycleIntent(LifecycleScreenIntent.Stop) }
    }

    LifecycleResumeEffect(aware, lifecycleOwner, lifecycleOwner = lifecycleOwner) {
        aware.obtainLifecycleIntent(LifecycleScreenIntent.Resume)
        onPauseOrDispose { aware.obtainLifecycleIntent(LifecycleScreenIntent.Pause) }
    }
}
