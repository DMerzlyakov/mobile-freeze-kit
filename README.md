# MobileFreezeKit

Набор независимых библиотек на Kotlin Multiplatform. Сейчас доступен модуль `mvi`. Лицензия — [MIT](LICENSE).

## Подключение

Добавьте `mavenCentral()` в репозитории проекта и зависимость в `commonMain`:

В `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

В `build.gradle.kts` KMP-модуля:

```kotlin
commonMain.dependencies {
    implementation("io.github.dmerzlyakov:freezekit-mvi:0.1.0")
}
```

Gradle выбирает платформенный артефакт автоматически. Для навигации по исходникам и чтения KDoc включите загрузку sources в IDE.

Поддерживаемые цели: Android (minSdk 29), JVM (Java 11), `iosArm64` и `iosSimulatorArm64`. Код библиотеки и Compose-интеграция находятся в `commonMain`. Отдельного SwiftUI API нет. Приложение управляет сроком жизни ViewModel и предоставляет владельца lifecycle экрана. На Desktop нужна реализация `Dispatchers.Main`, например `kotlinx-coroutines-swing`.

## Контракты экрана

| Контракт | Назначение |
| --- | --- |
| `ScreenState` | Состояние UI, доступное через `uiState: StateFlow<State>` |
| `ScreenIntent` | Намерение пользователя |
| `ScreenAction` | Внутренняя команда: действие, которое должен выполнить экран |
| `ScreenEvent` | Одноразовое событие для UI |
| `ScreenBootstrapData` | Неизменяемые параметры и правила конкретного экземпляра экрана |

Ниже — сокращённый пример экрана чата с двумя областями: **сообщения** (ввод и отправка) и **контакты** (добавление участника и открытие профиля). Импорты, DI, загрузка ленты, обработка ошибок и защита от повторных операций опущены. Это примеры API, а не реализация чата.

UseCase предоставляет приложение: `SendMessageUseCase.execute(chatId, text): Result<Unit>` и `AddChatContactUseCase.execute(chatId, contactId): Result<Int>`, где `Int` — обновлённое число участников. Также используется `SynchronizeChatMessagesUseCase.execute(chatId): Result<Unit>`. Реализация UseCase здесь не нужна; отмена корутин должна пробрасываться. Библиотека MVI не требует конкретного устройства domain-слоя.

```kotlin
private data class ChatScreenBootstrapData(val chatId: String) : ScreenBootstrapData

@Immutable
private data class ChatState(val draft: String, val participantCount: Int) : ScreenState {
    companion object {
        fun getInitial() = ChatState(draft = "", participantCount = 0)
        fun getExample() = ChatState(draft = "Привет!", participantCount = 3)
    }
}

private sealed interface ChatIntent : ScreenIntent {
    sealed interface Messages : ChatIntent {
        data class ChangeDraft(val text: String) : Messages
        data object Send : Messages
    }

    sealed interface Contacts : ChatIntent {
        data class Add(val contactId: String) : Contacts
        data class OpenProfile(val contactId: String) : Contacts
    }
}

private sealed interface ChatAction : ScreenAction {
    sealed interface Messages : ChatAction {
        data object Synchronize : Messages
    }

    sealed interface Contacts : ChatAction {
        data class Add(val contactId: String) : Contacts
    }
}

private sealed interface ChatEvent : ScreenEvent {
    data object MessageSent : ChatEvent
    data class OpenContact(val contactId: String) : ChatEvent
}
```

Группы отражают функции экрана. `Messages` объединяет работу с сообщениями, `Contacts` — с контактами. Внутри каждой группы могут быть и загрузка, и редактирование, и реакции на внешние изменения. Не нужно выделять Executor для каждого этапа операции.

## Вариант 1: обычная MVIViewModel

Все сообщения обрабатываются в одной ViewModel. Конфигурация доступна через `bootstrapData`, текущее состояние — через `uiState.value`. `initialState` можно передать из приложения или теста.

```kotlin
private class ChatViewModel(
    bootstrapData: ChatScreenBootstrapData,
    initialState: ChatState,
    private val sendMessageUseCase: SendMessageUseCase,
    private val synchronizeChatMessagesUseCase: SynchronizeChatMessagesUseCase,
    private val addChatContactUseCase: AddChatContactUseCase,
) : MVIViewModel<ChatState, ChatIntent, ChatAction, ChatEvent, ChatScreenBootstrapData>(
    initialState = initialState,
    bootstrapData = bootstrapData,
) {
    override suspend fun executeIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Messages.ChangeDraft -> applyDraft(intent.text)
            ChatIntent.Messages.Send -> sendMessage()
            is ChatIntent.Contacts.Add -> addContact(intent.contactId)
            is ChatIntent.Contacts.OpenProfile -> openProfile(intent.contactId)
        }
    }

    override suspend fun executeAction(action: ChatAction) {
        when (action) {
            ChatAction.Messages.Synchronize -> synchronizeMessages()
            is ChatAction.Contacts.Add -> addContact(action.contactId)
        }
    }



    private fun applyDraft(text: String) {
        updateState { it.copy(draft = text) }
    }

    private suspend fun sendMessage() {
        sendMessageUseCase
            .execute(bootstrapData.chatId, uiState.value.draft)
            .onSuccess {
                obtainAction(ChatAction.Messages.Synchronize)
                sendEvent(ChatEvent.MessageSent)
            }
        // Обработка ошибки и состояния отправки
    }

    private suspend fun synchronizeMessages() {
        synchronizeChatMessagesUseCase.execute(bootstrapData.chatId)
        // Применение обновлений ленты и обработка ошибки
    }

    private suspend fun addContact(contactId: String) {
        addChatContactUseCase
            .execute(bootstrapData.chatId, contactId)
            .onSuccess { count -> updateState { it.copy(participantCount = count) } }
        // Обработка ошибки и состояния добавления
    }

    private suspend fun openProfile(contactId: String) {
        sendEvent(ChatEvent.OpenContact(contactId))
    }
}
```

Intent описывает действие пользователя, Action — внутреннюю команду. После успешной отправки `obtainAction(Messages.Synchronize)` запускает синхронизацию независимо от завершения отправляющего обработчика. `Contacts.Add` вызывает тот же `addContact`, что и пользовательский Intent. `updateState` меняет State, `sendEvent` отправляет одноразовое событие UI.

## Вариант 2: ExecutorMVIViewModel

При росте экрана каждая область получает свой Executor. Состояние остаётся единым во ViewModel; Executor использует `ExecutorScope` и ту же конфигурацию `bootstrapData`.

**MessagesExecutor** принимает только сообщения группы `Messages`. Он объединяет Intent и Action, связанные с перепиской:

```kotlin
private class MessagesExecutor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val synchronizeChatMessagesUseCase: SynchronizeChatMessagesUseCase,
) : MVIExecutor<ChatState, ChatAction, ChatEvent, ChatScreenBootstrapData>() {
    suspend fun executeIntent(intent: ChatIntent.Messages) {
        when (intent) {
            is ChatIntent.Messages.ChangeDraft -> applyDraft(intent.text)
            ChatIntent.Messages.Send -> sendMessage()
        }
    }

    suspend fun executeAction(action: ChatAction.Messages) {
        when (action) {
            ChatAction.Messages.Synchronize -> synchronizeMessages()
        }
    }



    private fun applyDraft(text: String) {
        updateState { it.copy(draft = text) }
    }

    private suspend fun sendMessage() {
        sendMessageUseCase
            .execute(bootstrapData.chatId, uiState.draft)
            .onSuccess {
                obtainAction(ChatAction.Messages.Synchronize)
                sendEvent(ChatEvent.MessageSent)
            }
        // Обработка ошибки и состояния отправки
    }

    private suspend fun synchronizeMessages() {
        synchronizeChatMessagesUseCase.execute(bootstrapData.chatId)
        // Применение обновлений ленты и обработка ошибки
    }
}
```

**ContactsExecutor** отвечает за участников и профили. Пользовательский Intent и внутренняя команда добавления контакта используют один `addContact`:

```kotlin
private class ContactsExecutor(
    private val addChatContactUseCase: AddChatContactUseCase,
) : MVIExecutor<ChatState, ChatAction, ChatEvent, ChatScreenBootstrapData>() {
    suspend fun executeIntent(intent: ChatIntent.Contacts) {
        when (intent) {
            is ChatIntent.Contacts.Add -> addContact(intent.contactId)
            is ChatIntent.Contacts.OpenProfile -> openProfile(intent.contactId)
        }
    }

    suspend fun executeAction(action: ChatAction.Contacts) {
        when (action) {
            is ChatAction.Contacts.Add -> addContact(action.contactId)
        }
    }



    private suspend fun addContact(contactId: String) {
        addChatContactUseCase
            .execute(bootstrapData.chatId, contactId)
            .onSuccess { count -> updateState { it.copy(participantCount = count) } }
        // Обработка ошибки и состояния добавления
    }

    private suspend fun openProfile(contactId: String) {
        sendEvent(ChatEvent.OpenContact(contactId))
    }
}
```

Внутри Executor `uiState` — актуальный `State`, поэтому используется `uiState.draft`, без `.value`. Каждый Executor знает только свою группу Intent/Action и получает только свои UseCase. Методы `executeIntent`/`executeAction` объявляет конкретный класс — база не заставляет обрабатывать все сообщения экрана.

ViewModel направляет группы нужному Executor:

```kotlin
private class ExecutorChatViewModel(
    bootstrapData: ChatScreenBootstrapData,
    initialState: ChatState,
    private val messages: MessagesExecutor,
    private val contacts: ContactsExecutor,
) : ExecutorMVIViewModel<ChatState, ChatIntent, ChatAction, ChatEvent, ChatScreenBootstrapData>(
    initialState = initialState,
    bootstrapData = bootstrapData,
    executors = listOf(messages, contacts),
) {
    override suspend fun executeIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.Messages -> onMessagesIntent(intent)
            is ChatIntent.Contacts -> onContactsIntent(intent)
        }
    }

    override suspend fun executeAction(action: ChatAction) {
        when (action) {
            is ChatAction.Messages -> onMessagesAction(action)
            is ChatAction.Contacts -> onContactsAction(action)
        }
    }



    private suspend fun onMessagesIntent(intent: ChatIntent.Messages) = messages.executeIntent(intent)

    private suspend fun onContactsIntent(intent: ChatIntent.Contacts) = contacts.executeIntent(intent)

    private suspend fun onMessagesAction(action: ChatAction.Messages) = messages.executeAction(action)

    private suspend fun onContactsAction(action: ChatAction.Contacts) = contacts.executeAction(action)
}
```

Для каждой ViewModel создаются новые Executor — обычным конструктором или через DI factory. База автоматически привязывает их к одному State и BootstrapData. Повторная привязка экземпляра, включая повторение в списке, отклоняется. Обработчики при привязке не запускаются.

## Compose и lifecycle

Общая интеграция подходит для обоих вариантов. UI отправляет Intent через callback, а приложение обрабатывает `MessageSent` и `OpenContact`:

```kotlin
@Composable
private fun ChatRoute(
    viewModel: MVIViewModel<ChatState, ChatIntent, ChatAction, ChatEvent, ChatScreenBootstrapData>,
    onEvent: suspend (ChatEvent) -> Unit,
    content: @Composable (ChatState, (ChatIntent) -> Unit) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NotifyAboutScreenLifecycle(aware = viewModel)
    CollectMviEvents(viewModel = viewModel, onEvent = onEvent)

    content(state) { intent -> viewModel.obtainIntent(intent) }
}
```

Для реакции на lifecycle добавьте в любой из двух классов ViewModel:

```kotlin
override suspend fun executeLifecycleIntent(intent: LifecycleScreenIntent) {
    when (intent) {
        LifecycleScreenIntent.EnterComposition -> onEnterComposition()
        else -> Unit
    }
}

private fun onEnterComposition() {
    // Загрузка истории и участников, подписки на обновления
    // Результаты подписок можно передавать в obtainAction(...)
}
```

`EnterComposition/LeaveComposition` соответствуют входу/выходу из composition и смене ключей effect; `Start/Stop/Resume/Pause` — lifecycle владельца и очистке effects. Это не создание и очистка ViewModel. Повторные входы нужно учитывать при запуске подписок. `LeaveComposition` не гарантирует освобождение ресурсов при отменённом scope.

## Результаты между экранами

Фрагмент внутри ViewModel чата; `SelectedContact` — модель приложения с `contactId`, реализующая `ScreenShareData`:

```kotlin
private fun openContactPicker(onOpen: (ScreenShareRequest<SelectedContact>) -> Unit): Job {
    val request = ScreenShareRequest.create<SelectedContact>()
    val listener = setShareDataListener(request) { result ->
        obtainAction(ChatAction.Contacts.Add(result.contactId))
    }

    onOpen(request) // Передача запроса в параметры открываемого экрана
    return listener
}
```

Получатель подписывается до открытия отправителя. Для этого фрагмента открытие вызывается на Main, где listener запускается через Main.immediate до `onOpen`. Ненужный listener отменяется через возвращённый `Job`. Результат выбора преобразуется в команду `Contacts.Add`; в Executor-варианте её выполнит `ContactsExecutor`.

Фрагмент внутри ViewModel выбора контакта, получившего тот же `request`:

```kotlin
private suspend fun returnContact(request: ScreenShareRequest<SelectedContact>, contactId: String) {
    shareData(request, SelectedContact(contactId)).join()
    // Закрытие экрана выбора
}
```

`setShareDataListener` принимает первый результат, `collectShareData` — все последующие. Для общего канала типа обе стороны используют `ScreenShareRequest.broadcast<SelectedContact>()`. Без подписчика данные теряются; история и сохранение после перезапуска отсутствуют. `join()` ожидает отправку, а не завершение обработки получателем.

## Правила выполнения

- Каждый Intent, Action и lifecycle-сигнал обрабатывается в отдельной корутине `viewModelScope`; общей последовательной очереди нет. Конкурентность операций определяет экран.
- `obtainIntent` возвращает `Job`. `obtainAction` во ViewModel возвращает `Job`, в Executor — `Unit`. Отправка Action не ожидает его выполнения и не возвращает результат операции.
- Для последовательных шагов с результатом вызывайте UseCase или общий suspend-метод напрямую. Action подходит для асинхронных команд и уведомлений.
- `updateState` атомарен, его лямбда должна быть чистой и может выполняться повторно. После `suspend` читайте актуальный State, если он нужен для следующего решения.
- `uiEvent` — `SharedFlow` без повторной доставки. Без подписчиков событие теряется. Важный результат храните в State или данных приложения.
- `CollectMviEvents` подписан при lifecycle не ниже `STARTED`; остановка отменяет подписку и текущий обработчик события. Очистка ViewModel отменяет её задачи и подписки.
- Библиотека не перехватывает исключения. Обработку ошибок и отмену нужно учитывать в коде приложения.
