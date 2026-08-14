# Cityray Diagnostics Probe

Минимальный read-only probe для проверки штатного ECARX AdaptAPI на головном устройстве Geely Cityray / Boyue Cool.

## Результат первого теста на ГУ

Доступ к штатному API подтверждён на реальном устройстве с Android 11 / API 30:

- `Car.create()` вернул `CarImpl`;
- `getDiagnosticManager()` и `getDtcManager()` доступны;
- `getDtcInfos()` вернул 8 записей;
- `IDtcInfoWatcher` успешно зарегистрирован;
  но доступу к диагностике это не помешало.

Во всех восьми записях DTC-код пуст, ID/ECU имеют значения 1–8 и tick time
совпадает. Поэтому приложение не называет их восемью подтверждёнными ошибками:
это сырые vendor-записи без достаточной расшифровки.

## Реализованный эксперимент

Приложение выполняет только следующую цепочку:

```text
Car.create(context)
  -> ICar.getDiagnosticManager()
  -> IDiagnostics.getDtcManager()
  -> IDtcManager.getDtcInfos()
```

Также оно регистрирует только два наблюдателя:

- `IConnectable.IConnectWatcher`, если возвращённый `ICar` реализует `IConnectable`;
- `IDtcManager.IDtcInfoWatcher` для обновления списка DTC.

Запись vehicle properties, очистка DTC, CAN-команды, диагностические shell-команды, `IShCommand`, `IDiagnosticMonitor.setMonitorEnable()` и низкоуровневый `ecarx.car` в первом APK не используются.

Кнопка **«Очистить лог»** очищает только текст на экране и не взаимодействует с автомобилем.

## Открытие в Android Studio

Откройте корневую папку `CityrayDiagnosticsProbe`. Проект использует:

- Kotlin + Jetpack Compose;
- `minSdk 26` — совместимо с Android 9 (API 28);
- `compileSdk/targetSdk 35`, Build Tools 36.0.0;
- JDK из состава Android Studio (Embedded JDK/JBR); Windows-путь из переносимой копии удалён.


Собрать debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Актуальный APK также лежит в корне проекта:

```text
CityrayDiagnosticsProbe-v0.2.0-debug.apk
```

## Первый тест на ГУ

1. Поставить автомобиль на парковку и включить ГУ.
2. Установить APK обычным способом или через `adb install -r`.
3. Запустить **Cityray Diagnostics Probe**.
4. Дождаться `IConnectable.onConnected()` либо нажать **«Повторить»** после полной загрузки ГУ.
5. Сохранить скриншот экрана и logcat с тегом `CityrayDiagProbe`.

На Mac установку и сбор этих двух файлов можно выполнить готовым скриптом:

```bash
./scripts/collect_probe_result.sh
```

Скрипт требует ровно одно подключённое и авторизованное ADB-устройство. Результат
будет сохранён в `probe-results/<дата-время>/`. При необходимости путь к другому
APK можно передать первым аргументом.

Интерпретация результата:

- три статуса `AVAILABLE`, `DTC count: 0` — API доступен, список успешно прочитан и пуст;
- `SecurityException` — vendor service проверяет permission/UID/signature;
- `ClassNotFoundException` или `NoClassDefFoundError` — ECARX API не виден classloader обычного APK;
- `Car.create() returned null` — класс найден, но объект не создан; стоит повторить после полной загрузки ГУ;
- `getDiagnosticManager() returned null` — Car доступен, но diagnostics manager отсутствует в данной реализации/конфигурации;

Значения `ECU type`, `status` и `tick time` намеренно показываются как raw: их семантика не была придумана без подтверждённого vendor mapping.
