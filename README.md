# MobileFreezeKit

Набор переиспользуемых библиотек на Kotlin Multiplatform для общих задач в приложениях. Модули предназначены для независимого подключения: можно использовать только нужные возможности.

## Статус

В репозитории доступен модуль `mvi`. Публикация артефактов пока не настроена; Maven-координат для подключения нет.

## mvi

Базовая инфраструктура MVI в пакете `com.dmerzliakov.freezekit.mvi`:

- `MVIViewModel` управляет состоянием через `StateFlow`, обрабатывает намерения и действия и отправляет одноразовые события через `SharedFlow`;
- `ScreenState`, `ScreenIntent`, `ScreenAction` и `ScreenEvent` задают контракты экрана;
- `ScreenShareRequest` и `ScreenShareData` обеспечивают адресный обмен результатами или общий канал по типу данных;
- `CollectMviEvents` получает события при жизненном цикле не ниже `STARTED`, используя актуальный обработчик;
- `NotifyAboutScreenLifecycle` передаёт события жизненного цикла в `ScreenLifecycleAware`.

Обмен данными выполняется в памяти без повторной доставки: получатель подписывается до отправки. Данные без подписчиков теряются, перезапуск приложения их не сохраняет. Обработчики и подписки ViewModel отменяются при её очистке; ошибки не перехватываются библиотекой.

Настроены цели Android (minSdk 29), JVM (Java 11), iOS ARM64 и iOS Simulator ARM64. Проверки iOS требуют macOS и Xcode. Код и Compose-интеграция находятся в `commonMain`; отдельный SwiftUI API не предоставляется.

Модуль использует версии из общего каталога: Kotlin 2.4.20, Compose Multiplatform runtime 1.12.1, JetBrains AndroidX Lifecycle 2.11.0 и kotlinx.coroutines 1.11.0.

Если исходный модуль `mvi` включён в ваш Gradle-проект, добавьте зависимость в нужный source set:

```kotlin
commonMain.dependencies {
    implementation(project(":mvi"))
}
```

Приложение предоставляет владельца жизненного цикла и управляет сроком жизни ViewModel. На JVM требуется реализация `Dispatchers.Main`, например `kotlinx-coroutines-swing` для Compose Desktop. Библиотека не добавляет её транзитивно.

### Жизненный цикл экрана

`NotifyAboutScreenLifecycle` передаёт сообщения `LifecycleScreenIntent` в `ScreenLifecycleAware` через метод `obtainLifecycleIntent(intent): Job`. Для их обработки ViewModel переопределяет `protected suspend fun executeLifecycleIntent(intent: LifecycleScreenIntent)`. Тип `Intent` конкретного экрана задаётся отдельно.

`EnterComposition` и `LeaveComposition` обозначают вход в composition и выход из неё, включая переустановку effect при смене получателя или владельца. `Start`, `Stop`, `Resume` и `Pause` относятся к жизненному циклу переданного владельца; `Stop` и `Pause` также отправляются при очистке соответствующих effects.

Каждое сообщение обрабатывается в отдельной корутине `viewModelScope` без ожидания предыдущей обработки. Очистка ViewModel отменяет задачи; `LeaveComposition` не гарантирует освобождение ресурсов, особенно если scope уже отменён. Обязательную очистку следует привязывать к владельцу ресурса и отмене его работы.
