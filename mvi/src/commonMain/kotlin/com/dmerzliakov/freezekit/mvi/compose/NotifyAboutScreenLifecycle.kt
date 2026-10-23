package com.dmerzliakov.freezekit.mvi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dmerzliakov.freezekit.mvi.aware.ScreenLifecycleAware

/**
 * Передаёт события жизненного цикла экрана получателю [aware].
 * Создание и уничтожение соответствуют входу в composition и выходу из неё, а также смене получателя или владельца.
 * Запуск, остановка, возобновление и пауза соответствуют жизненному циклу [lifecycleOwner] и очистке effects.
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
        aware.onScreenCreate()

        onDispose {
            aware.onScreenDestroy()
        }
    }

    LifecycleStartEffect(aware, lifecycleOwner, lifecycleOwner = lifecycleOwner) {
        aware.onScreenStart()
        onStopOrDispose { aware.onScreenStop() }
    }

    LifecycleResumeEffect(aware, lifecycleOwner, lifecycleOwner = lifecycleOwner) {
        aware.onScreenResume()
        onPauseOrDispose { aware.onScreenPause() }
    }
}
