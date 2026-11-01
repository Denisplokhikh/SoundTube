# SoundTube Android — локальный плеер и загрузчик

Нативное Android-приложение-компаньон для десктопного SoundTube: качает аудио прямо
на устройстве (`youtubedl-android` = yt-dlp + ffmpeg), хранит библиотеку в Room,
играет через Media3. Без облака и без сервера; синхронизация с десктопом — JSON или LAN.

## Статус

| Шаг | Что | Готово |
| --- | --- | --- |
| 1 | Gradle, version catalog, манифест, сервис | ✅ |
| 2 | Room (entities/DAO), доменные модели, детерминированные ID | ✅ |
| 3 | Загрузчик: YoutubeDL init, WorkManager, репозиторий | ✅ |
| 4 | Media3-плеер, контроллер, очередь | ✅ |
| 5 | Compose UI: навигация, экраны, мини-плеер | ✅ |
| 6 | JSON export/import, LAN-sync | ⏳ |

## Загрузчик (шаг 3)

* `MelodownApp` инициализирует `YoutubeDL` + `FFmpeg` в фоне при старте; состояние движка —
  `YoutubeDLClient.state` (`NotInitialized → Initializing → Ready/Failed`), обновление
  бинарника — `updateBinaries()` (канал nightly).
* Ссылка сначала проходит через `getTracks` (`--dump-json --ignore-errors`): трек сразу
  попадает в Room с детерминированным id, потом ставится в `WorkManager`
  (`AudioDownloadWorker`).
* Ссылка на плейлист/сет SoundCloud тоже работает: `--dump-json` отдаёт по строке JSON
  на каждый трек, все они попадают в Room, а задачи ставятся в **цепочку** WorkManager
  (`playlist:<sha256(url)>`, `REPLACE` для первой, `APPEND_OR_REPLACE` для остальных) —
  одновременно качается ровно один файл, иначе телефон захлёбывается.
* id задачи в цепочке берётся из тега `track:<id>`, а не из `inputData`: WorkManager
  перекладывает output предыдущей задачи в input следующей (`OverwritingInputMerger`),
  и `track_id` в input перезаписывался id первого трека — остальные 40 «находили» его файл
  и мгновенно завершались успехом. Поэтому `Result.success()` теперь без данных.
* Скачивание — foreground-сервис с прогрессом в уведомлении и в `WorkInfo.progress`.
  Опции: `bestaudio[ext=m4a]/bestaudio`, `--extract-audio --audio-format m4a`,
  `--embed-thumbnail --add-metadata`.
* Готовый файл переезжает в `SoundTube/<Исполнитель>/<Исполнитель> - <Название>.m4a`
  (app-specific `Music`), считается SHA-256, трек линкуется в `sys:youtube` / `sys:soundcloud`.
* Отмена задачи убивает нативный процесс yt-dlp; сетевые ошибки ретраятся с backoff,
  «private video» / «not available» — нет.
* «Очистить» на вкладке загрузок снимает все задачи WorkManager (одиночные, цепочки
  плейлистов и бэклог), гасит уже запущенные yt-dlp и ставит надгробия незавершённым
  трекам. Без надгробий список читался бы из Room и очередь выглядела бы непустой.
  Скачанные файлы и блок «Готово» не трогаются.
* Если файл трека уже лежит на диске, повторный запуск задачи сразу завершается успехом:
  иначе после рестарта появлялся бы дубль `Название (2).m4a`.
* Ошибки yt-dlp переводятся на русский (`domain/util/DownloadErrors.kt`): обрыв сети,
  приватный/удалённый трек, неподдерживаемая ссылка. В UI такая ошибка показывается красным
  и с кнопкой «Повторить» — раньше она выглядела как обычная серая подпись.
* При обрыве сети `getInfo` повторяется до трёх раз с паузой 2 с: телефон с VPN часто
  на секунду теряет DNS, и ссылка «не скачивается» без всякой видимой причины.
* В debug-сборке ссылку можно запустить без тапов:
  `adb shell am start -n com.example.localplayer/.MainActivity --es debug_url <url>`
  (активности нужен `launchMode="singleTop"` — иначе интент в уже открытое окно
  приходит, но `onNewIntent` не вызывается).
* Журнал видно в приложении: «Настройки → Журнал» (`core/AppLog.kt`, кольцевой
  буфер на 400 записей, дублируется в logcat с тегом `Melodown`). Кнопки «Копировать»,
  «Очистить», «Свернуть». Логи пишут загрузчик, репозиторий, движок и плеер — на телефоне
  без компьютера этого достаточно, чтобы понять, почему ссылка не скачалась.

## Плеер (шаг 4)

* `media/PlaybackService.kt` — `MediaSessionService` с `ExoPlayer`: `USAGE_MEDIA` /
  `AUDIO_CONTENT_TYPE_MUSIC`, `setHandleAudioBecomingNoisy(true)` (пауза при отключении
  наушников), `WAKE_MODE_LOCAL` (играет с выключенным экраном). Уведомление с обложкой,
  управлением и экраном блокировки рисует сам Media3.
* `media/PlayerController.kt` — единственный мост UI ↔ сервис: держит `MediaController`,
  публикует `PlayerState` (`StateFlow`) с текущим треком, позицией, очередью, shuffle и
  повтором, плюс команды `playQueue/playPause/next/previous/seekTo/...`.
  Позиция обновляется таймером 2 раза в секунду, остальное — по событиям плеера.
* `ui/player/PlayerViewModel.kt` — грузит скачанные треки из Room и отдаёт их в контроллер.
* Проверка без тапов (debug): `adb shell am start -n com.example.localplayer/.MainActivity
  --ez debug_play true` — играет всю библиотеку.

## Интерфейс (шаг 5)

Material 3 с собственной терминальной темой: OLED-чёрный фон, панели `#121412`
с рамкой 1.dp вместо теней, шалфейно-зелёный акцент `#A8D5A2` и моноширинная
типографика. Динамические цвета Material You не используются — палитра
фиксированная и повторяет десктопный SoundTube.

Три вкладки в нижней навигации (как в сайдбаре на ПК) и общий мини-плеер над ней.

* **Библиотека** — скачанная библиотека: поиск по названию/исполнителю, сортировка
  (артист → название → новые), «Играть всё» и перемешивание. Строки компактные:
  номер, обложка 40.dp, название/артист, длительность и меню из трёх точек
  («В плейлист», «Удалить»), разделитель 0.5.dp. Текущий трек подсвечен,
  у играющего вместо номера — эквалайзер.
  Удаление стирает файл с диска и ставит надгробие в базе, поэтому трек исчезает
  из библиотеки, очереди и плейлистов. «В плейлист» показывает пользовательские
  плейлисты и умеет создать новый; системные площадок наполняются сами.
* **Плейлисты** — системные (`sys:youtube`, `sys:soundcloud`) и пользовательские
  плейлисты из Room. Внутри — треки и кнопка «Играть всё» с начала; треки, которых
  нет на диске, помечены и не запускаются (плееру нечего играть).
* **Загрузки** — ссылка, очередь с прогрессом, ETA, отменой и повтором, кнопка
  «Очистить» в заголовке очереди (останавливает все загрузки и убирает незавершённые
  задачи), отдельный блок «Готово» с кнопкой «Слушать». Разрешение на уведомления
  просим здесь же.
* **Настройки** — отдельный экран за шестерёнкой в шапке: тема
  (система/светлая/тёмная, хранится в SharedPreferences), состояние и обновление
  yt-dlp, «Докачать недостающее», уборка дублей, размер библиотеки, уведомления,
  журнал с копированием и версия приложения.

Мини-плеер виден на всех вкладках, пока в плеере есть трек: фон `#0E100E`,
верхняя рамка 1.dp, зелёный прогресс 2.dp, обложка, название/артист, тайминги,
круглая белая кнопка play/pause и следующий. Полноэкранный плеер — не отдельный
маршрут, а лист поверх приложения: тап по мини-плееру раскрывает его, свайп вверх
вытягивает следом за пальцем, а свайп вниз по обложке (или по шапке, или системный
back) убирает обратно в мини-плеер. Обложка — первая в списке, поэтому её и удобно
тянуть: список отдаёт жест листу только когда сам уже прокручен в начало, а вверх
очередь листается как обычно. Плавность обеспечивает `ui/player/PlayerSheet.kt`:
жест и анимация двигают одну и ту же величину, поэтому лист не дёргается при
отпускании пальца. Внутри — большая обложка, перемотка, shuffle/repeat и очередь.

Шапки экранов и нижняя навигация сами обрабатывают системные отступы
(edge-to-edge), поэтому контент не заезжает под статус-бар и жест-навигацию.
Полноэкранный плеер накрывает нижнюю навигацию собой, плейлист и настройки её скрывают.

Для отладки вкладку можно открыть сразу:
`adb shell am start -n com.example.localplayer/.MainActivity --es debug_tab settings`
(допустимо `tracks`, `playlists`, `downloads`, `settings`).

## Сборка

JDK 17 и Android SDK 35. Wrapper 8.9 уже лежит в репозитории:

```bash
./gradlew :app:assembleDebug
```

Либо просто откройте папку `android/` в Android Studio — она подхватит wrapper.
`local.properties` с `sdk.dir` создаётся студией и в git не попадает.

Только `arm64-v8a` и `x86_64` ABI: нативных бинарников под armeabi-v7a у youtubedl-android нет.

AGP не умеет работать с не-ASCII путём, поэтому в `gradle.properties` включён
`android.overridePathCheck=true`. Юнит-тесты при этом запускаются только из ASCII-пути:
из папки с кириллицей JVM не находит классы тестов (`ClassNotFoundException`). Обход —
подключить диск через `subst` и запускать оттуда:

```powershell
subst S: "C:\Users\minio\OneDrive\Рабочий стол\melodown"
cd S:\android
gradle :app:testDebugUnitTest
```

## Релиз APK

Релиз собирает `.github/workflows/release.yml` по пушу тега. Два вида тегов:

| Тег | Что происходит |
| --- | --- |
| `v1.2.1` | Десктоп-инсталляторы (tauri-action) + APK в том же релизе |
| `android-v1.2.1` | Только APK: десктопная джоба пропускается, релиз создаёт джоба `android` |

Релиз с тегом `android-*` создаётся с `--latest=false`: десктоп ищет обновления через
`/releases/latest` (`app/core/updates.py`) и не должен получать андроидный релиз вместо своего.

Ключ подписи в git не попадает (`.gitignore`), он лежит в **секретах окружения** `android-env`
(Settings → Environments → android-env → Environment secrets), а джоба `android` объявляет
`environment: android-env`, без этого секреты не видны:

| Secret | Значение |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.jks` (на Windows: `[Convert]::ToBase64String([IO.File]::ReadAllBytes(...))`) |
| `ANDROID_KEYSTORE_PASSWORD` | пароль хранилища |
| `ANDROID_KEY_ALIAS` | алиас ключа |
| `ANDROID_KEY_PASSWORD` | пароль ключа |

Без этих переменных окружения release локально подписывается отладочным ключом
(`~/.android/debug.keystore`), так что `./gradlew :app:assembleRelease` работает без настройки.
Сменить ключ задним числом нельзя: APK с другой подписью не встанет обновлением поверх старого.

## Детерминированные ID (контракт синхронизации)

| Источник | ID |
| --- | --- |
| YouTube | `yt:<video_id>` |
| SoundCloud | `sc:<artist>/<track-slug>` (нижний регистр) |
| Прочее | `sha256:<hex от канонического URL>` |

Канонический URL: нижний регистр схемы/хоста, без `www.`, без хвостового `/`,
без `utm_*`, `si`, `feature`. Реализация — `domain/util/TrackIds.kt`, тесты —
`app/src/test/java/com/example/localplayer/domain/util/TrackIdsTest.kt`.

Короткие ссылки SoundCloud (`on.soundcloud.com/…`) офлайн не разворачиваются, поэтому для
них сначала спрашиваем метаданные: `yt-dlp` отдаёт канонический `webpage_url`
(`soundcloud.com/<artist>/<track>`), и только потом строится `sc:`-id. Проверено на
`https://on.soundcloud.com/…` → `sc:xanzlighter/priroda-chuvstv`.

Правила, которые нельзя нарушать, иначе синхронизация разъедется:

* никаких автоинкрементных id у треков и плейлистов;
* удаление — только `isDeleted = true` + `updatedAt = now` (файл с диска стирается);
* системные плейлисты всегда `sys:youtube` / `sys:soundcloud`;
* путь к файлу — свойство устройства, в идентичности трека он не участвует.

## Структура

```
app/src/main/java/com/example/localplayer/
├── MelodownApp.kt              @HiltAndroidApp, WorkManager через Hilt
├── MainActivity.kt             Activity, тема, debug-intent
├── core/                       AppLog — журнал, который видно в приложении
├── di/                         Room/DAO, WorkManager в Hilt
├── data/local/db/              AppDatabase, entity/, dao/, Mappers.kt
├── data/local/files/           AudioFileManager: пути, перенос в библиотеку, SHA-256
├── data/downloader/            YoutubeDLClient, AudioDownloadWorker, уведомления
├── data/repository/            DownloadRepository (очередь + история), LibraryRepository
├── domain/model/               Track, Playlist, DownloadState, RepeatMode, SourcePlatform
├── domain/util/                TrackIds, SystemPlaylists, FileNameSanitizer
├── media/                      PlaybackService, PlayerController, PlayerState
└── ui/
    ├── SoundTubeApp.kt         каркас: вкладки, навигация, мини-плеер
    ├── navigation/             Routes
    ├── components/             карточки/кнопки темы, TrackRow, TrackArtwork, EmptyState
    ├── theme/                  Color.kt (палитра), Type.kt (monospace), Theme.kt
    ├── player/                 PlayerViewModel, MiniPlayer, PlayerSheet, NowPlayingScreen
    └── screens/                tracks/, playlists/, downloads/, settings/
```

## База

`localplayer.db`, версия 1, `exportSchema = true` — схемы пишутся в `app/schemas/`
и коммитятся. Миграций пока нет: при изменении моделей поднимайте `version`
и добавляйте `Migration`.
