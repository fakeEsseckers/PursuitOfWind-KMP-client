# Pursuit Of Wind — промт для разработки клиентской части (KMP)

> Как использовать: положите содержимое папки `repo/` из этого набора в корень пустого репозитория (копия этого файла уже лежит там как `docs/prompts/client-phase.ru.md`), откройте Claude Code в этой папке, включите plan mode (`/plan`) и скажите: «Прочитай docs/prompts/client-phase.ru.md и начни с Issue #1».

---

## 0. Роль и режим работы

Ты — Staff Mobile Engineer с глубокой экспертизой Kotlin Multiplatform, Compose Multiplatform, Android и iOS. Ты строишь публичный репозиторий, который будет смотреть технический интервьюер или CTO. Код должен выглядеть как начало настоящего продукта, а не как учебный проект: продуманные границы модулей, тесты на доменную логику, честные ADR, рабочий демо-режим.

Правила общения и артефактов:

- Со мной общайся **по-русски**. Всё, что попадает в репозиторий (код, комментарии, KDoc, README, ADR, сообщения коммитов, описания PR и issues), пиши **по-английски**.
- Перед каждой задачей показывай короткий план и жди подтверждения, если задача затрагивает архитектуру или больше 5 файлов. Мелкие задачи делай сразу.
- После каждой задачи давай мне 3–5 строк по-русски: что сделано, как проверить, что осталось.
- Не выдумывай API библиотек. Перед использованием любой библиотеки сверяйся с актуальной документацией через Context7 MCP или официальные источники. Версии бери последние **стабильные** и фиксируй в `gradle/libs.versions.toml`.
- Если чего-то не знаешь или требование противоречиво — спроси меня, а не угадывай.

---

## 1. Продукт

**Pursuit Of Wind**. Tagline: *Know when to go. Know who's driving.*

**Проблема.** Кайтсёрферу, живущему вдали от воды, до спота 1–3 часа дороги. Прогнозы в разных приложениях расходятся, а цифры надо самому перевести в решение: хватит ли ветра под мой вес и мои кайты, подходит ли направление для этого берега, не слишком ли порывисто, сколько часов останется после дороги. Ехать одному дорого и скучно, а договариваться в чате «кто завтра едет?» получается поздно.

**Решение.** Райдер один раз вносит вес, уровень, набор кайтов и свои споты с временем дороги. Приложение сводит прогнозы нескольких моделей и само отвечает: ехать или нет, когда, на какой спот, с каким кайтом — и кто из его кайт-компании едет и есть ли место в машине.

**Killer feature — «окно + машина».** Один экран с ответом:
«**ДА** · Сб · Nordstrand Bay · 13:00–17:00 · 18–22 kn · кайт 9 м² · выезжай в 10:30 · уверенность высокая · Андрей едет, 2 места» — или «**НЕТ** · ветер только 2 часа и порывы +12 kn».

**Пользователь.** Райдер среднего и продвинутого уровня, живущий в 1–3 часах от воды, катается с компанией знакомых.

**Позиционирование.** Рынок прогнозов для кайта насыщен (Windy.app, Windmaster, myWind, KiteReady, Boardiction). Мы не делаем ещё один прогноз. Наше отличие — решение «ехать или нет», связанное с координацией поездки внутри своей компании.

---

## 2. Объём этой фазы

Эта фаза — **только клиент на KMP**: Android + iOS, общий код, API на заглушках (фейковый сервер внутри приложения), демо-режим. Сервер будет отдельной фазой в отдельном репозитории и реализует тот же контракт.

**Входит:**

- общий движок решений `windengine` (чистый Kotlin, будет переиспользован сервером);
- OpenAPI-контракт и DTO;
- фейковый сервер внутри приложения, реализующий контракт;
- слой данных: сетевой клиент, кеш, офлайн, токены;
- 6 экранов на Compose Multiplatform;
- демо-режим со сценариями и управляемым временем;
- локальные уведомления о новом окне (Android и iOS);
- приглашение в компанию по deep link;
- тесты, статанализ, CI, README, ADR, демо-APK в GitHub Releases.

**Не входит (сознательно):**

- реальный сервер, база, авторизация на сервере;
- FCM/APNs push от сервера (оставить интерфейс с no-op реализацией);
- реальные прогнозы из внешних API (только через будущий сервер);
- карта спотов и выбор точки на карте;
- маршрутизация и расчёт времени в пути (время дороги вводится вручную);
- чат, лента, лайки, профили других пользователей;
- оплата, подписки, аналитика, crash reporting;
- коррекция прогноза по станциям, приливы, журнал сессий.

---

## 3. Стек и правила выбора

| Область | Выбор | Примечание |
| --- | --- | --- |
| Язык | Kotlin (последняя стабильная), K2 | `explicitApi()` в `windengine` и `contract` |
| UI | Compose Multiplatform | общий UI для Android и iOS |
| Навигация | JetBrains Navigation Compose (multiplatform), type-safe routes | 6 экранов, без своего фреймворка |
| Presentation | `androidx.lifecycle` ViewModel (multiplatform), `StateFlow<UiState>` + `onIntent()` | без MVI-библиотек |
| DI | Koin (KMP) | тест `verify()` графа в CI |
| Сеть | Ktor Client: OkHttp (Android), Darwin (iOS), MockEngine для фейка | |
| Сериализация | kotlinx.serialization | |
| Время | `kotlin.time.Instant` / `Clock` из stdlib + kotlinx-datetime для часовых поясов | проверить актуальный API через Context7 |
| Кеш | Room KMP с bundled SQLite driver | показывает опыт Room на KMP |
| Настройки | DataStore Preferences (KMP) | профиль, флаги, dedupe уведомлений |
| Логи | Kermit | никогда не логировать токены |
| Тесты | kotlin-test, kotlinx-coroutines-test, Turbine, Kotest property testing, Roborazzi (опционально), Kover | |
| Статанализ | detekt, ktlint (через плагин), Android Lint | |
| Android | minSdk 26, target/compile — последние стабильные | edge-to-edge, predictive back |
| iOS | iOS 16+ | Compose внутри `ComposeUIViewController` |

**Важно про структуру Gradle.** Используй структуру, совместимую с AGP 9: Android-приложение — отдельный модуль `androidApp` с `com.android.application`; KMP-модули с Android-таргетом — через `com.android.kotlin.multiplatform.library`. Сверься с актуальной документацией JetBrains/Google и зафиксируй решение в ADR.

**Политика expect/actual.** Платформенные сервисы (уведомления, фоновые задачи, безопасное хранилище, шаринг) — это интерфейсы в common-коде с платформенными реализациями, которые внедряются через Koin. `expect/actual` используй только для мелких функций и фабрик (драйвер БД, движок Ktor).

---

## 4. Структура репозитория

```text
PursuitOfWind-KMP-client/       # this repo (client); the server lives in a separate repo
├── androidApp/                 # Android entry point: Application, MainActivity, WorkManager, notifications, deep links
├── iosApp/                     # Xcode project: SwiftUI App hosting ComposeUIViewController, Info.plist, BG tasks
├── shared/
│   ├── windengine/             # PURE Kotlin: units, sectors, sizing, hour verdicts, windows, decisions (android, ios, jvm)
│   ├── contract/               # API DTOs mirroring api/openapi.yaml (android, ios, jvm)
│   ├── data/                   # Ktor client, FakePursuitOfWindServer, Room, DataStore, repositories, AppError (android, ios, jvm for tests)
│   └── app/                    # ViewModels, Compose UI, navigation, DI, demo mode; exports iOS framework "PursuitOfWindKit"
├── api/
│   └── openapi.yaml            # single source of truth for the server phase
├── docs/
│   ├── adr/                    # 0001-*.md ...
│   ├── architecture.md
│   ├── domain-rules.md
│   └── demo.md                 # 60-second demo script
├── scripts/                    # setup.sh, check.sh, build-demo-apk.sh
├── .devcontainer/
├── .github/workflows/          # ci.yml, release.yml
├── .claude/                    # agents, skills, settings (already provided)
├── .mcp.json
├── CLAUDE.md
├── LICENSE                     # Apache-2.0
└── README.md
```

**Правила зависимостей (проверяются тестом или Gradle-проверкой):**

```text
windengine  → (только stdlib, kotlinx-datetime)
contract    → kotlinx.serialization
data        → windengine, contract, Ktor, Room, DataStore, Koin
app         → windengine, contract, data, Compose, Navigation, Koin
androidApp  → app
iosApp      → PursuitOfWindKit (framework from :shared:app)
```

`windengine` и `contract` должны собираться под JVM: сервер будет жить в отдельном репозитории и подключит их как опубликованные Maven-артефакты (см. раздел 17). Разбиение на feature-модули для 6 экранов избыточно; зафиксируй в ADR, при каком размере проекта разделил бы.

Пакеты: `io.github.esseckers.pursuitofwind.engine`, `io.github.esseckers.pursuitofwind.contract`, `io.github.esseckers.pursuitofwind.data`, `io.github.esseckers.pursuitofwind.ui` (namespace выбран: `io.github.esseckers.pursuitofwind`; задай его одной константой в Gradle, `applicationId` Android-приложения — такой же).

---

## 5. Доменная модель и движок решений (`windengine`)

Это сердце продукта. Реализуй строго по правилам ниже, покрой табличными и property-тестами. Подробные формулы и тестовые векторы — в скиле `wind-domain-rules` (`.claude/skills/wind-domain-rules/`), он является источником истины. Перенеси правила в `docs/domain-rules.md`.

### 5.1. Типы

- `value class WindSpeed(val metersPerSecond: Double)` с конвертацией в kn, km/h, Beaufort. Внутри — всегда м/с.
- `value class Bearing(val degrees: Double)` — направление, **откуда** дует ветер, нормализованное в [0, 360).
- `data class Sector(val from: Bearing, val to: Bearing)` — дуга по часовой стрелке, поддерживает переход через 0°.
- `enum class SkillLevel { BEGINNER, INTERMEDIATE, ADVANCED }`.
- `data class RiderProfile(weightKg, level, quiverM2: List<Double>, minWindKn?, maxWindKn?, minSessionHours = 2.0, maxDriveMinutes = 180, units)`.
- `data class SpotConfig(id, name, timeZone, rideableSectors, dangerSectors, driveMinutes, dataQuality)`.
- `data class ForecastHour(start: Instant, byModel: Map<ModelId, ModelWind>, precipMm, tempC, thunderRisk, isDaylight)`.
- `enum class Verdict { GO, MARGINAL, NO_GO }`, `enum class Reason { TOO_LIGHT, TOO_STRONG, GUSTY, OFFSHORE_DANGER, DIRECTION_UNRIDEABLE, THUNDERSTORM, RAIN, COLD, DARK, UNDERPOWERED_FOR_QUIVER, OVERPOWERED_FOR_QUIVER, TOO_FAR, WINDOW_TOO_SHORT }`.

### 5.2. Оценка часа

1. **Ветер часа** — консенсус моделей: средняя скорость, средние порывы, направление через векторное среднее (не арифметическое!). Если векторное среднее не определено (модели дуют в противоположные стороны), берётся направление модели с наивысшим приоритетом: `ecmwf` → `icon` → `gfs`.
2. **Диапазон по уровню** (по умолчанию, переопределяется личными min/max):
   - BEGINNER: 12–22 kn, допустимый разброс порывов (gust − mean) ≤ 8 kn;
   - INTERMEDIATE: 12–28 kn, ≤ 10 kn;
   - ADVANCED: 10–35 kn, ≤ 14 kn.
3. **Направление:** в `dangerSectors` → NO_GO `OFFSHORE_DANGER`; вне всех `rideableSectors` → NO_GO `DIRECTION_UNRIDEABLE`.
4. **Погода:** высокий риск грозы → NO_GO `THUNDERSTORM`; осадки > 2 мм/ч → MARGINAL `RAIN`; температура < 5 °C → MARGINAL `COLD`; час вне светлого времени (начало до восхода или конец позже чем за 30 мин до заката) → NO_GO `DARK`.
5. **Ветер:** ниже минимума → NO_GO `TOO_LIGHT`; выше максимума → NO_GO `TOO_STRONG`; разброс порывов выше допустимого → MARGINAL при превышении до 3 kn, иначе NO_GO `GUSTY`.
6. **Размер кайта** (эвристика, не рекомендация по безопасности):
   `effectiveKn = mean + 0.3 × (gust − mean)`, `idealM2 = weightKg / effectiveKn × 2.2`.
   Кандидаты из набора — размеры в диапазоне [0.8 × ideal, 1.15 × ideal]; берётся ближайший к ideal, при равенстве (с точностью 1e-9) — меньший. Нет кандидатов: ideal меньше самого маленького кайта → NO_GO `OVERPOWERED_FOR_QUIVER`; ideal больше самого большого → NO_GO `UNDERPOWERED_FOR_QUIVER`; ideal попал в «дыру» между размерами → берётся ближайший меньший кайт и MARGINAL `UNDERPOWERED_FOR_QUIVER`.
7. **Итог часа** — худший из результатов правил; `reasons` — все не-GO причины, отсортированные по серьёзности.

### 5.3. Окна и решение

- **Окно** — максимальная последовательность часов со статусом GO; одиночные MARGINAL-часы между двумя GO-часами включаются в окно (со штрафом к качеству), MARGINAL на краях и два MARGINAL подряд окно разрывают.
- **Время выезда:** `leaveBy = windowStart − driveMinutes − 30 мин` (подготовка). Если `now > leaveBy`, полезная часть окна считается от `now + drive + 30 мин`.
- Окно подходит, если полезная длина ≥ `minSessionHours`; иначе причина `WINDOW_TOO_SHORT`. Спот с `driveMinutes > maxDriveMinutes` исключается с `TOO_FAR`.
- **Уверенность** — по медиане разброса средней скорости между моделями за часы окна: ≤ 3 kn HIGH, ≤ 6 kn MEDIUM, иначе LOW; одна модель → UNKNOWN.
- **Счёт окна:** `usableHours × quality × confidenceFactor`, где quality = 1.0 минус 0.15 за каждый MARGINAL-час; confidenceFactor: HIGH 1.0, MEDIUM 0.85, LOW 0.6, UNKNOWN 0.8. При равенстве выигрывает более короткая дорога.
- **Решение на день** (`DayDecision`): `Go(window, spot, kite, leaveBy, confidence, alternatives)` или `NoGo(primaryReason, details, nextGoDay?)`. Главная причина отказа: если были GO-часы, но окно короткое — `WINDOW_TOO_SHORT`; иначе самая частая причина по светлым часам всех спотов, при равенстве — более серьёзная.
- Все вычисления — чистые функции от входных данных и переданного `now: Instant`. Никаких `Clock.System` внутри движка.

### 5.4. Время

- Часы прогноза — `Instant` в UTC. Границы дня и отображение — в часовом поясе спота (IANA).
- Обязательные тесты на переход на летнее и зимнее время: `Europe/Berlin`, 29.03.2026 (23-часовой день) и 25.10.2026 (25-часовой день).

---

## 6. API-контракт

Контракт-first: сначала `api/openapi.yaml` (OpenAPI 3.1), потом DTO в `contract`, потом фейковый сервер. Любое изменение контракта идёт в этом порядке (скил `api-contract-workflow`).

**Общие правила:** префикс `/v1`; JSON; время — ISO-8601 UTC; скорость ветра — м/с; ошибки — `application/problem+json` с полями `type`, `title`, `status`, `code`; мутации принимают заголовок `Idempotency-Key`; авторизация — `Authorization: Bearer`.

| Метод и путь | Назначение |
| --- | --- |
| `POST /v1/auth/anonymous` | анонимная регистрация устройства → `accessToken`, `refreshToken`, `userId` |
| `POST /v1/auth/refresh` | обновление токенов |
| `GET /v1/me/profile` · `PUT /v1/me/profile` | профиль райдера |
| `GET /v1/spots/catalog` | каталог спотов с секторами |
| `GET /v1/me/spots` · `PUT /v1/me/spots/{spotId}` · `DELETE /v1/me/spots/{spotId}` | мои споты, время дороги, избранное |
| `GET /v1/spots/{spotId}/forecast?from=&hours=72` | прогноз по моделям (ветер, порывы, направление), погода, светлое время |
| `POST /v1/crews` · `GET /v1/me/crews` | создать компанию, мои компании |
| `POST /v1/crews/join` | вступить по `inviteCode` |
| `GET /v1/crews/{crewId}/members` | участники |
| `GET /v1/crews/{crewId}/rides?from=` · `POST /v1/crews/{crewId}/rides` | поездки компании, создать поездку из окна |
| `POST /v1/rides/{rideId}/seats` | занять место → `201` или `409 SEATS_FULL` / `409 ALREADY_JOINED` |
| `DELETE /v1/rides/{rideId}/seats/me` · `DELETE /v1/rides/{rideId}` | выйти из поездки, отменить поездку (водитель) |
| `GET /v1/crews/{crewId}/feed?since=` | события: `RIDE_CREATED`, `SEAT_TAKEN`, `SEAT_RELEASED`, `RIDE_CANCELLED`, `MEMBER_JOINED` |
| `PUT /v1/me/push-token` | регистрация push-токена (в фейке — no-op) |

---

## 7. Фейковый сервер (заглушки)

Заглушка живёт **на HTTP-уровне**, а не подменой репозиториев: весь слой данных (сериализация, заголовки, маппинг ошибок, ретраи) работает по-настоящему, а переход на реальный сервер — это смена `baseUrl` и движка Ktor.

- `FakePursuitOfWindServer` — обработчик Ktor `MockEngine` с маленьким роутером (метод + шаблон пути).
- Состояние в памяти под `Mutex`, засевается из `Seed` (детерминированно, без `Random` без сида).
- Соблюдает контракт: коды, `problem+json`, `Idempotency-Key` (повтор возвращает сохранённый ответ), проверку Bearer-токена.
- Прогнозы генерируются процедурно из параметров сценария (3 модели: `ecmwf`, `icon`, `gfs`) с детерминированным шумом.
- **Симуляция сети:** `NORMAL`, `SLOW` (задержка 2 с), `FLAKY` (30% ответов 503), `OFFLINE` (IOException). Задержка идёт через инжектируемый `Clock`/диспетчер, чтобы в тестах время было виртуальным.
- **Контрактные тесты** в `jvmTest` модуля `data`: каждый ответ фейка валидируется по `api/openapi.yaml` (JVM-валидатор OpenAPI — выбери актуальную библиотеку). Это гарантия, что сервер можно будет подставить без изменений клиента.
- Тот же фейк используется в тестах репозиториев и ViewModel.

**Окружения** выбираются в точке входа (`androidApp`, `iosApp`) и передаются в `initKoin(environment)`:
`FAKE` (по умолчанию в этой фазе), `REMOTE(baseUrl)` (для будущего сервера), `DEMO(scenario)`.

---

## 8. Демо-режим

Демо — продуктовая функция для того, кто впервые открывает приложение с GitHub. За 60 секунд он должен увидеть killer feature без регистрации и сети.

- **Вход:** кнопка «Try the demo» на приветственном экране, переключатель в настройках и deep link `pursuitofwind://demo/{scenario}`.
- Демо работает на `FakePursuitOfWindServer` со сценарием и **управляемыми часами** (`ControllableClock`), которые используют и фейк, и движок.
- Демо-данные хранятся в **отдельной базе и DataStore** и никогда не смешиваются с данными пользователя. Выход из демо полностью сбрасывает их.
- Постоянный бейдж **DEMO** в верхней панели.
- Готовая персона: райдер 80 кг, INTERMEDIATE, кайты 7 / 9 / 12 м²; три вымышленных спота (`Nordstrand Bay`, `Salt Lagoon`, `Lake Weiss`) с разным временем дороги; компания «Weekend Crew» из 4 человек.

**Сценарии (детерминированные):**

| Сценарий | Что показывает |
| --- | --- |
| `saturday-on` | GO: Сб 13–17, 18–22 kn, кайт 9 м², уверенность HIGH; Андрей едет, 2 места |
| `gusty-trap` | сильный средний ветер, но порывы +14 kn → NO-GO `GUSTY`: решение умнее сырых цифр |
| `offshore-trap` | хороший ветер, но оффшор на ближнем споте → NO-GO там, GO на дальнем: «не туда, а сюда» |
| `too-short` | окно 1,5 ч при дороге 2 ч → NO-GO `WINDOW_TOO_SHORT` |
| `models-disagree` | ECMWF 20 kn, ICON 17 kn, GFS 11 kn → GO, но уверенность LOW и подсказка «модели расходятся» |
| `car-fills-up` | по демо-времени участники занимают места 3 → 0; последняя попытка ловит `409` и показывает откат оптимистичного обновления |

**Панель демо** (в настройках): выбор сценария; время +1 ч / +6 ч / следующий день; «добавить событие компании»; «показать уведомление об окне сейчас»; режим сети NORMAL / SLOW / FLAKY / OFFLINE; сброс.

Опиши 60-секундный сценарий показа в `docs/demo.md` и используй его для GIF в README.

---

## 9. Экраны и UX (не больше 6)

1. **Welcome / Profile** — имя, вес, уровень, набор кайтов (чипы), единицы; кнопка «Try the demo».
2. **Today («Go?»)** — главный экран: карточка-ответ GO/NO-GO по избранным спотам на сегодня и ближайшие 2 дня; окно, кайт, время выезда, уверенность, поездки компании. Для NO-GO — главная причина простыми словами и ближайший GO-день.
3. **Spot detail** — почасовая шкала на 72 ч: столбцы скорости с цветом вердикта, порывы поверх, стрелки направления, полоса разброса моделей, подсветка окон; по тапу на час — причины. Рисуется **своим Canvas**, без библиотек графиков.
4. **Spots** — мои споты из каталога, время дороги, избранное; пометка «сектора не проверены» для данных с `dataQuality = UNVERIFIED`.
5. **Crew** — участники, приглашение (share sheet), поездки по окнам; «Я еду» (создать поездку с местами и точкой встречи), «Занять место» с оптимистичным обновлением и откатом при `409`.
6. **Settings** — единицы, уведомления, окружение, панель демо, дисклеймер.

**UX-требования:**

- Цвет никогда не единственный сигнал: GO/MARGINAL/NO-GO — это иконка + текст + цвет.
- Состояния каждого экрана: загрузка (скелетоны), контент, пусто, ошибка с повтором, устаревшие данные («обновлено 2 ч назад»).
- Pull-to-refresh; офлайн показывает последний кеш.
- Доступность: `contentDescription`, корректные роли и семантика, масштабирование шрифта до 200% без обрезки текста.
- Строки — Compose Multiplatform resources, языки EN и RU.
- Тёмная и светлая темы; своя дизайн-система (цвета, типографика, отступы) в одном месте.
- Дисклеймер на Today и в настройках: приложение помогает принять решение, но не заменяет оценку условий на споте и не является рекомендацией по безопасности.

---

## 10. Платформенные части

**Android (`androidApp`):**

- WorkManager: периодическая задача (раз в час) — обновить прогноз при наличии сети, пересчитать решения на 3 дня, показать локальное уведомление о **новом** GO-окне. Дедупликация по стабильному id `spotId + date + startHour`, хранится в DataStore.
- Канал уведомлений, разрешение `POST_NOTIFICATIONS` (Android 13+) с объяснением до системного диалога.
- Deep links: `pursuitofwind://invite/{code}` и `pursuitofwind://demo/{scenario}`.
- Edge-to-edge, predictive back, Share sheet для приглашения.
- Release-сборка с R8 и правилами для kotlinx.serialization; подпись демо-APK ключом из секретов CI.

**iOS (`iosApp` + `iosMain`):**

- SwiftUI `App` с `ComposeUIViewController` из фреймворка `PursuitOfWindKit`.
- Локальные уведомления через `UNUserNotificationCenter` (вызовы из Kotlin в `iosMain`).
- Фоновое обновление через `BGTaskScheduler` (best effort, честно описать ограничения iOS в README).
- URL scheme `pursuitofwind://` для приглашений и демо; `UIActivityViewController` для шаринга.
- Безопасное хранилище токенов — Keychain.

**Безопасное хранилище на Android:** не используй устаревший `androidx.security:security-crypto`. Шифруй токены ключом AES-GCM из Android Keystore и храни в DataStore.

---

## 11. Данные, ошибки, безопасность

- Репозитории отдают `Flow` из Room (single source of truth) и обновляют кеш из сети: stale-while-revalidate.
- `sealed interface AppError { Network, Unauthorized, Conflict(code), Server(status), Unknown }`; маппинг исключений Ktor в одном месте; UI получает только `AppError`.
- Ретраи с экспоненциальной задержкой только для идемпотентных запросов; мутации — с `Idempotency-Key`.
- Автоматический refresh токена при `401` с защитой от параллельных обновлений (`Mutex`).
- Оптимистичное обновление при «Занять место»: мгновенно в UI, откат и понятное сообщение при `409`.
- Никаких секретов в репозитории; `local.properties`, ключи подписи и `.env` в `.gitignore`.
- В режимах FAKE и DEMO никакие данные не покидают устройство — сказать об этом в README.

---

## 12. Качество

**Тесты:**

- `windengine` (основной вес): табличные тесты по векторам из скила `wind-domain-rules`; property-тесты (нормализация углов, принадлежность сектору при переходе через 0°, монотонность размера кайта по ветру, окна не пересекаются); DST-тесты.
- `data`: репозитории на `FakePursuitOfWindServer`; refresh токена; офлайн и FLAKY; идемпотентность; контрактные тесты по OpenAPI.
- `app`: ViewModel с Turbine и виртуальным временем; Compose UI-тест критического пути (демо `saturday-on` → GO-карточка → создать поездку).
- Скриншот-тесты ключевых экранов в демо-сценариях (Roborazzi) — если укладываемся в срок.
- Kover-отчёт; цель — покрытие `windengine` ≥ 90%, по остальному без гонки за процентами.

**Статанализ:** detekt + ktlint + Android Lint без предупреждений; проверка правил зависимостей модулей; `verify()` графа Koin.

**CI (GitHub Actions):**

- `ci.yml` на PR и `main`: ubuntu — lint, тесты JVM/Android, контрактные тесты, Kover, `assembleDebug` (APK как артефакт); macOS — `iosSimulatorArm64Test` и сборка `iosApp` для симулятора без подписи (на `main` и PR с меткой `ios`).
- `release.yml` по тегу `v*`: подписанный демо-APK в GitHub Release с заметками.
- Кеширование Gradle, configuration cache.

**Dev environment:** основная разработка — локально на Mac (Android Studio + Xcode), там же эмулятор Android и iOS-симулятор. `.devcontainer` для GitHub Codespaces — запасной вариант для общего кода и тестов (JDK 21, Android SDK command-line tools, машина 4 ядра / 16 ГБ); эмулятор и iOS там не работают. `scripts/setup.sh`, `scripts/check.sh` (всё, что гоняет CI), `scripts/build-demo-apk.sh`.

---

## 13. Витрина на GitHub

- **README:** проблема одной строкой → GIF демо 20 с → «Try it in 60 seconds» (скачать APK из Releases, открыть демо) → как это работает (схема модулей и поток данных) → движок решений (правила коротко + ссылка на `docs/domain-rules.md`) → ключевые инженерные решения со ссылками на ADR → тестирование → roadmap (серверная фаза) → дисклеймер → лицензия → атрибуция источников данных.
- **ADR** (минимум): 0001 KMP + Compose Multiplatform; 0002 структура модулей и правила зависимостей (AGP 9); 0003 политика версий зависимостей (написаны в Issue #1); 0004 движок решений как чистый Kotlin для трёх рантаймов; 0005 фейковый сервер на HTTP-уровне и контракт-first; 0006 Room KMP против SQLDelight; 0007 Koin против compile-time DI; 0008 демо-режим и управляемое время; 0009 локальные уведомления до появления сервера; 0010 переиспользование движка и контракта сервером из отдельного репозитория.
- **Issues** — по плану ниже, с метками `phase:client`, `area:*`, `good first issue`.
- CI-бейджи, Kover-бейдж, скриншоты светлой и тёмной темы.

---

## 14. План работ и issues

Работаем по issue: одна issue — одна ветка — один PR с описанием по шаблону из скила `github-issue-workflow`.

| # | Issue | Критерии приёмки (кратко) | Зависит от |
| ---: | --- | --- | --- |
| 1 | **Bootstrap**: Gradle-структура (AGP 9), version catalog, модули-заглушки, detekt/ktlint, `.devcontainer`, `ci.yml`, `scripts/` | `./scripts/check.sh` зелёный локально и в CI; APK собирается; `iosApp` открывается в Xcode | — |
| 2 | **windengine: типы и правила часа** | WindSpeed/Bearing/Sector, оценка часа, размер кайта; все векторы из скила проходят; property-тесты секторов | 1 |
| 3 | **windengine: окна и решение** | окна, leaveBy, уверенность, счёт, `DayDecision`, DST-тесты; покрытие ≥ 90% | 2 |
| 4 | **API-контракт** | `api/openapi.yaml`, DTO в `contract`, примеры в спецификации | 1 |
| 5 | **FakePursuitOfWindServer** | роутер, состояние, Seed, идемпотентность, problem+json, симуляция сети; контрактные тесты зелёные | 4 |
| 6 | **Data layer** | Ktor client, auth + refresh, Keystore/Keychain хранилище, Room-кеш, репозитории, офлайн | 5 |
| 7 | **Дизайн-система и Today** | тема, компоненты вердикта, Today с GO/NO-GO, все состояния экрана | 3, 6 |
| 8 | **Spot detail и график** | Canvas-график 72 ч, окна, разброс моделей, причины по тапу | 7 |
| 9 | **Profile, Spots, Crew** | онбординг, споты, компания, приглашение, поездки, оптимистичное «занять место» с откатом | 7 |
| 10 | **Демо-режим** | 6 сценариев, ControllableClock, панель, изоляция данных, deep link | 5, 9 |
| 11 | **Платформа** | WorkManager + уведомления + dedupe; iOS BG + уведомления; deep links; share | 6, 10 |
| 12 | **Витрина** | README, ADR 0001–0010, `docs/demo.md`, GIF, скриншоты, `release.yml`, демо-APK в Releases, публикация `windengine` и `contract` в GitHub Packages | все |

Ориентир: рабочий клиент на Android к концу issue 9 (≈ 7 дней), отполированное демо на двух платформах к концу issue 12 (≈ 12 дней). Если отстаём — первыми режем скриншот-тесты и iOS BG-задачи, но не тесты движка и не демо-режим.

---

## 15. Definition of Done (для каждой issue)

- [ ] Код соответствует правилам модулей и политике expect/actual.
- [ ] Новые доменные правила покрыты тестами; `./scripts/check.sh` зелёный.
- [ ] Никаких предупреждений detekt/ktlint/Lint; нет `TODO` без ссылки на issue.
- [ ] Строки в ресурсах (EN + RU), доступность проверена.
- [ ] Субагент `code-reviewer` прогнан, замечания закрыты или осознанно отклонены в описании PR.
- [ ] Если принято архитектурное решение — есть ADR.
- [ ] Коммиты в формате Conventional Commits; описание PR на английском; мне — короткое резюме по-русски.

---

## 16. Как работать с агентами и скилами

В `.claude/` уже лежат субагенты и скилы. Используй их так:

| Задача | Кто делает | Скилы |
| --- | --- | --- |
| Структура модулей, Gradle, ADR по архитектуре | `kmp-architect` | `kmp-module-conventions`, `adr-writing` |
| Движок решений и его тесты | `wind-engine-engineer` | `wind-domain-rules`, `testing-playbook` |
| OpenAPI, DTO, фейковый сервер, контрактные тесты | `api-contract-engineer` | `api-contract-workflow`, `fake-server-and-demo` |
| Экраны, дизайн-система, график | `compose-ui-engineer` | `compose-screen-recipe` |
| Android-платформа | `android-platform-engineer` | `kmp-module-conventions` |
| iOS-платформа | `ios-platform-engineer` | `kmp-module-conventions` |
| CI, devcontainer, скрипты, релиз | `build-ci-engineer` | `github-issue-workflow` |
| Недостающие тесты, флаки | `test-engineer` | `testing-playbook` |
| Ревью после каждой issue (только чтение) | `code-reviewer` | все релевантные |
| README, ADR, demo.md, тексты issues | `docs-writer` | `adr-writing`, `github-issue-workflow` |

Независимые задачи (например, issue 2–3 и issue 4–5) можно вести параллельно разными субагентами.

---

## 17. Передача на серверную фазу

Сервер не пишем. Он будет отдельным репозиторием (например, `PursuitOfWind-server`), поэтому готовим клиент к переиспользованию:

- `api/openapi.yaml` — полный и проверенный контрактными тестами; версия контракта фиксируется тегом.
- `windengine` и `contract` собираются под JVM и не зависят от Android/iOS.
- `windengine` и `contract` публикуются как Maven-артефакты в GitHub Packages по тегу `v*` (`maven-publish`, семантические версии). Сервер подключает их по версии и берёт спецификацию из того же тега. На время разработки допустим Gradle composite build (`includeBuild`) с локальной копией клиентского репозитория. Выбор фиксируется в ADR 0010.
- В `docs/architecture.md` — раздел «Server phase»: Ktor + PostgreSQL, планировщик, который тянет прогнозы (Open-Meteo или аналог — проверить условия использования для коммерции), пересчитывает окна тем же `windengine` и шлёт push (FCM/APNs) компании при новом окне или поездке.
- Переключение клиента: `Environment.REMOTE(baseUrl)` + реальный `PushTokenRegistrar`.

---

## 18. Первое действие

Прочитай `CLAUDE.md`, скилы `kmp-module-conventions` и `github-issue-workflow`. Затем в plan mode предложи план Issue #1 (список файлов и команд проверки), сверь актуальные версии Kotlin, Compose Multiplatform, AGP, Ktor, Room, Koin через Context7 и дождись моего «ок».
