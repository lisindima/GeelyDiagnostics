# Geely Diagnostics

Read-only приложение на Kotlin и Jetpack Compose для автомобилей Geely, чья
штатная головная система предоставляет ECARX AdaptAPI.

## Возможности

Приложение содержит четыре вкладки:

- **Диагностика** — DTC, сгруппированные по ECU; несколько ошибок одного блока
  показываются отдельными строками;
- **Сенсоры** — динамический список поддерживаемых `ISensor` и live raw-значения;
- **Автомобиль** — поддерживаемые сведения `ICarInfo` о типе автомобиля,
  комплектации и установленном оборудовании;
- **Функции** — функции `IVehicle`, поддержку которых подтвердила конкретная
  машина, их текущие raw-значения, зоны и допустимые значения.

Каталоги строятся по публичным константам той версии AdaptAPI, которая реально
установлена на ГУ. Перед чтением каждого элемента вызывается соответствующий
`is…Supported`. Поэтому наличие константы в API не принимается за доказательство
поддержки конкретным автомобилем.

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
Design или Split. В файле есть четыре preview для ГУ `1440×1920`, по одному на
каждую вкладку.

## Сборка

Проект использует `minSdk 26`, `compileSdk/targetSdk 35`, Build Tools 36.0.0 и
Java 17.

```bash
./gradlew :app:assembleDebug
```

Готовый APK:

```text
GeelyDiagnostics-v0.4.0-debug.apk
```

SHA-256: `76AFB1864D1CB50B727C791C1B6B63AFA117287451E4C5B3463382B3F81C7092`.

Пакет: `com.geelydiagnostics.app`. Logcat tag: `GeelyDiagnostics`.

Для установки и сбора screenshot/logcat можно использовать:

```bash
./scripts/collect_diagnostics_result.sh
```

Скрипт требует ровно одно подключённое и авторизованное ADB-устройство. Результат
сохраняется в `diagnostics-results/<дата-время>/`.
