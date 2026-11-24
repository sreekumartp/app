# The K.I.S.S model for mastering AI (Android)

An offline Android app for The K.I.S.S model for mastering AI, a knowledge map of AI and ML: 243 topics in eight levels, including an Applied AI section (robotics, autonomous driving, healthcare, science, finance, industry and more), from arithmetic to
2024–26 frontier research, with prerequisite paths, a syllabus and learning resources for every topic.

## What the app does

- **Phone-first layout.** Bottom navigation (Home · Map · Curriculum · Roles), a compact top bar with
  Search and Settings, and a Home screen with your K.I.S.S funnel, a "Continue" card for your role and
  the next topics to convert. A short intro explains the model on first launch.
- **Readable map.** On phones the map opens at a readable zoom with level jump chips and larger tap areas.
- **Topic sheet.** A "Where am I?" selector marks your rung in one tap; tabs split the topic into
  Ladder, Study, Resources and Path.
- **Settings with backup.** Success goal, your background, and backup/restore of all progress.

- **Works offline.** The atlas page, d3 and the fonts ship inside the APK. Only the external learning
  resources need a connection.
- **Opens resources in Chrome Custom Tabs**, so reading a paper or course page and coming back is quick.
- **Back button** closes the topic popup, the search list or the Curriculum view before leaving the app.
- **Share** on any topic sends its syllabus and resource links to any app.
- **K.I.S.S. ladder.** Every topic and level spells out Information (aware), Knowledge (can teach it),
  Skill (can build with it) and Success (others recognise, use or build on it). Success comes in six
  forms: SME recognition, paper or talk, new technique, adoption, influence and money. Each topic shows
  its natural forms; 50 key topics have hand-written markers for every form. You can also type your own
  definition of success for any topic; it replaces the suggestion on that topic's ladder. Pick your own success goal
  in the header. Mark your rung per topic; the overview shows your
  funnel and suggests what to convert next.
- **Role learning paths.** A Roles view covers 20 AI jobs in five families (build products, research,
  platform & infra, trust & safety, bridge roles). Each role has 5–6 ordered phases; every phase lists
  topics in study order, a teach-back (Knowledge), a capstone project (Skill) and a milestone (Success).
  Pick your background (new to tech, software engineer, data analyst, ML practitioner) and the path
  marks what you already know and where to start. Your marked rungs give a readiness score and the gaps
  to close next. Link straight to a role with `#role-<id>`.
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
syllabi in `SYL`, roles in `ROLES` and `PHASES`, resources in `LIB` and `RES`, and the ladder in `KISS` and `LEVEL_KISS`.

Third-party: d3 (ISC, see `assets/d3-LICENSE.txt`); Instrument Sans, Bricolage Grotesque and JetBrains
Mono (SIL Open Font License, see `assets/fonts/OFL-*.txt`).

## Web version on GitHub Pages

`.github/workflows/pages.yml` publishes the same offline page (`app/src/main/assets/`) to GitHub Pages
whenever it changes on `main`, or when run by hand from the Actions tab. One-time setup: in the
repository's **Settings → Pages**, set **Source** to **GitHub Actions**. The site is then served at
`https://<owner>.github.io/<repo>/`.

