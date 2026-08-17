# Geely Diagnostics

Read-only приложение на Kotlin и Jetpack Compose для автомобилей Geely, чья
штатная головная система предоставляет ECARX AdaptAPI.

> Неофициальный исследовательский проект. Он не связан с Geely или ECARX и не
> заменяет сертифицированную автомобильную диагностику.

## Возможности

Приложение содержит пять вкладок:

- **Диагностика** — DTC, сгруппированные по ECU; несколько ошибок одного блока
  показываются отдельными строками;
- **Сенсоры** — динамический список поддерживаемых `ISensor` и live raw-значения;
- **Автомобиль** — поддерживаемые сведения `ICarInfo` о типе автомобиля,
  комплектации и установленном оборудовании;
- **Функции** — функции `IVehicle`, поддержку которых подтвердила конкретная
  машина, их текущие raw-значения, зоны и допустимые значения;
- **Лог** — отдельный журнал ECARX с локальной кнопкой очистки.

Интерфейс поддерживает светлую и тёмную цветовые схемы и автоматически следует
системной теме Android. Отдельного переключателя внутри приложения нет: при
изменении night mode Compose перестраивает экран с системной схемой.

Каталоги строятся по публичным константам той версии AdaptAPI, которая реально
установлена на ГУ. Перед чтением каждого элемента вызывается соответствующий
`is…Supported`. Поэтому наличие константы в API не принимается за доказательство
поддержки конкретным автомобилем.

## Структура UI

- `MainActivity.kt` — lifecycle и перенос callback-данных в `AppUiState`;
- `GeelyDiagnosticsApp.kt` — общий app shell, заголовок и навигация;
- `DiagnosticsTab.kt`, `SensorsTab.kt`, `VehicleTab.kt`, `FunctionsTab.kt`,
  `LogTab.kt` — независимая реализация каждой вкладки;
- `TabComponents.kt` — общие карточки и элементы каталогов;
- `ReadOnlyModels.kt` — UI-модели и контракт sink.

## Гарантия Read Only

ECARX-клиент приложения использует только:

- `Car.create()` и getters менеджеров;
- `getDtcInfos()` и DTC watcher;
- `isSensorSupported()`, sensor getters и sensor listener;
- `isCarInfoSupported()` и `getCarInfo*()`;
- `isFunctionSupported()`, `getFunctionValue()`,
  `getSupportedFunctionValue()` и `getSupportedFunctionZones()`.

В app-коде отсутствуют `setFunctionValue`, `setCustomizeFunctionValue`,
`setMonitorEnable`, DTC clearing, `IShCommand`, CAN/shell-команды и
низкоуровневые записи vehicle properties. Кнопка **«Очистить лог»** удаляет
только текст журнала из UI.

## Compose Preview

Откройте
`app/src/debug/java/com/geelydiagnostics/app/DiagnosticsPreview.kt` и включите
Design или Split. В файле есть preview для пяти вкладок ГУ `1440×1920`, а также
отдельные варианты диагностики в светлой и тёмной темах.

## Совместимость

- Android 8.0+ (`minSdk 26`), проверено на Android 11 / API 30;
- требуется штатная реализация `com.ecarx.xui.adaptapi.*` в system image;
- набор доступных сенсоров, сведений и функций зависит от модели, комплектации и
  версии ПО ГУ;
- отсутствие элемента в UI означает, что API машины не подтвердил его поддержку
  либо чтение завершилось ошибкой — это не доказательство отсутствия оборудования.

## Сборка

Проект использует `minSdk 26`, `compileSdk/targetSdk 35`, Build Tools 36.0.0 и
Java 17.

```bash
./gradlew :app:assembleDebug
```

Готовый APK публикуется отдельно от исходников в
[GitHub Releases](https://github.com/lisindima/GeelyDiagnostics/releases).
Для версии `v0.4.2` имя файла — `GeelyDiagnostics-v0.4.2-debug.apk`.

SHA-256: `B12383BE20F8C2DF9AFA9307A98D7BC6A1F54CC320A8F2C634415B6A4B57AF5E`.

Пакет: `com.geelydiagnostics.app`. Logcat tag: `GeelyDiagnostics`.

Для установки и сбора screenshot/logcat можно использовать:

```bash
./scripts/collect_diagnostics_result.sh
```

Скрипт по умолчанию использует APK из `app/build/outputs/apk/debug/app-debug.apk`
и требует ровно одно подключённое и авторизованное ADB-устройство. Путь к
скачанному APK из Releases можно передать первым аргументом. Результат сохраняется
в `diagnostics-results/<дата-время>/`.

## Благодарности

Спасибо [Salat39](https://github.com/Salat39) за вклад в развитие проекта.
