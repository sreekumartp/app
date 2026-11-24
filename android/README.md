# AI & ML Atlas for Android

An offline Android app for the AI & ML Knowledge Atlas: 227 topics in eight levels, from arithmetic to
2024–26 frontier research, with prerequisite paths, a syllabus and learning resources for every topic.

## What the app does

- **Works offline.** The atlas page, d3 and the fonts ship inside the APK. Only the external learning
  resources need a connection.
- **Opens resources in Chrome Custom Tabs**, so reading a paper or course page and coming back is quick.
- **Back button** closes the topic popup, the search list or the Curriculum view before leaving the app.
- **Share** on any topic sends its syllabus and resource links to any app.
- **K.I.S.S. ladder.** Every topic and level spells out Information (aware), Knowledge (can teach it),
  Skill (can build with it) and Success (others recognise, use or build on it). Success comes in six
  forms: SME recognition, paper or talk, new technique, adoption, influence and money. Each topic shows
  its natural forms; 50 key topics have hand-written markers for every form. Pick your own success goal
  in the header. Mark your rung per topic; the overview shows your
  funnel against the 99 / 0.5 / 0.4 / 0.1 baseline and suggests what to convert next.
- **Role learning paths.** A Roles view covers 18 AI jobs in five families (build products, research,
  platform & infra, trust & safety, bridge roles). Each path is derived from the map: core topics at
  Skill (or Knowledge for PM, governance and educator roles), their direct prerequisites at Knowledge,
  and everything further back at Information. Your marked rungs give a readiness score and the gaps to
  close next. Link straight to a role with `#role-<id>`.
- **Remembers progress.** Ticked subtopics and your ladder rungs persist between launches.
- **Follows the system theme** (light/dark) and font size, and draws edge to edge on Android 15.

Requires Android 8.0 (API 26) or newer.

## Build locally

You need JDK 17+ and the Android SDK (platform 35). Point Gradle at the SDK with `ANDROID_HOME` or a
`local.properties` file containing `sdk.dir=/path/to/Android/Sdk`, then:

```sh
cd android
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected device or emulator
```

Or open the `android/` folder in Android Studio and press Run.

`assembleRelease` produces a minified APK. It is signed with the debug key so it installs right away;
add your own signing config in `app/build.gradle.kts` before publishing to Google Play.

## CI

`.github/workflows/android-atlas.yml` builds both APKs and runs lint on every push that touches
`android/`. Download the APKs from the run's **Artifacts** section (`aiml-atlas-apks`).

## Project layout

```
android/
├── app/src/main/assets/        index.html (the atlas), d3.min.js, fonts/
├── app/src/main/java/.../      MainActivity.kt (WebView host), AtlasBridge.kt (share)
└── app/src/main/res/           theme, colours, adaptive launcher icon
```

To update the content, edit the web atlas and regenerate the app page with
`python3 tools/build_app_page.py <atlas.html> app/src/main/assets/index.html`. Topics live in the `RAW` array, their
syllabi in `SYL`, roles in `ROLES`, resources in `LIB` and `RES`, and the ladder in `KISS` and `LEVEL_KISS`.

Third-party: d3 (ISC, see `assets/d3-LICENSE.txt`); Instrument Sans, Bricolage Grotesque and JetBrains
Mono (SIL Open Font License, see `assets/fonts/OFL-*.txt`).
