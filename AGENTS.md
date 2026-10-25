# Agent development guide

## Scope and repository state

Agnihotra is a native Kotlin Android application for location-based sunrise and
sunset timings and practice reminders. Target India first, English and Hindi,
and Android 8.0 / API 26 or newer. The app is free, ad-free, and requires no login.

The application is in `app/`, with a committed-source Gradle wrapper and pinned
dependencies in `gradle/libs.versions.toml`. Solar logic is in `core/`, saved
state in `data/`, and Compose screens in `ui/`. Android integrations are in
`location/`, `reminders/`, `widget/`, and `analytics/`. Inspect the current tree
before changing it; do not report planned features or checks as implemented or
passed. Local builds work without `app/google-services.json`; connecting actual
Firebase Analytics still requires that local project configuration.

## Read before changing code

- Read the relevant code and any more-specific `AGENTS.md` instructions first.
- Local planning context may exist in `docs/project-brief.md` and
  `docs/development-setup.md`. Consult it when useful, but the repository must
  remain understandable and buildable without these ignored files.
- Keep `docs/` untracked and ignored. Do not force-add its contents.
- Keep `README.md` strictly about the app, its features, availability, and privacy.
  Put agent workflows and development instructions here, not in the README.
- `CLAUDE.md` is a filesystem link to this file. Maintain `AGENTS.md` as the single
  source of agent instructions; preserve the link rather than duplicating text.
- Respect existing user changes and avoid unrelated edits, commits, or resets.

## Product behavior

- Support one home location and multiple named saved locations, with GPS-assisted
  setup and manual entry. The app must work when location permission is denied.
- Check for travel on app open/resume using foreground location. Prompt at 25 km
  from home, accounting for location freshness and accuracy. Avoid repeated
  prompts and do not silently replace the home location.
- Provide preparation reminders and event-time alerts with chime and vibration.
- Include a home-screen widget, monthly timetable, and reviewed mantra text.
  Mantra recordings are deferred until an approved source is available; do not
  add placeholder playback controls or unlicensed recordings. Post-practice music
  supports user-selected audio and the separate `ReleaseConfiguration.practiceMusic`
  catalog. Two owner-supplied Om recordings are bundled. The center navigation
  action starts a timed audio session immediately with saved preferences (30 minutes
  by default), looping audio until the timer ends or the user stops it. Keep this
  user-chosen session length separate from the sunrise/sunset offering time.
  The event-countdown flow still supports offering confirmation and an optional
  quiet pause afterward; do not invent a fixed mandatory ritual duration.
- Timetable export uses Android's document picker without storage permissions.
  CSVs identify the place, coordinates, time zone, date, and event times. Preserve
  Unicode and quote cells safely; sharing happens only on an explicit user action.
- Time format, optional seconds, mantra reading preferences, and the keep-awake
  option must persist. Apply formatting consistently across screens, alerts, and
  widgets. Keep-awake is limited to visible countdown/mantra screens.
- Each widget can follow the selected place or bind to a saved place. Widget
  configuration must not silently change reminder locations. Opening a fixed
  widget selects that place explicitly in the app.
- Use a modern, accessible UI with warm ivory/copper/amber and a dusk dark theme.
  Make the selected place and next event immediately clear.
- Home and active location are separate concepts. The proposed default is to
  use the active location consistently for displayed timings, widget, and alerts.

## Architecture and implementation

- Prefer Kotlin, Gradle Kotlin DSL, Jetpack Compose, and Material 3 with a custom
  theme. Use ViewModels, coroutines, and StateFlow for screen state.
- Keep solar calculations pure Kotlin and independent of Android UI and services.
  Inject time and platform dependencies where it makes behavior testable.
- Use Room for saved locations and DataStore for small preferences when needed.
- Use AlarmManager for time-sensitive alerts and Jetpack Glance for widgets.
  WorkManager is not an exact event-alarm scheduler.
- Start with one application module and clear feature/data/timing boundaries.
  Add abstraction layers, dependencies, and modules only for concrete needs.
- Pin compatible stable tooling and dependencies. Check official compatibility
  documentation before selecting or upgrading Kotlin, AGP, Gradle, JDK, and SDK.
- Minimum SDK is 26. Choose compile/target SDK to satisfy current Play policy and
  validate behavior changes; recheck policy before release.
- Use the Gradle wrapper; do not require globally installed Gradle
  or Kotlin. Keep local SDK/JDK paths out of tracked files.
- `com.nipunrautela.agnihotra` matches the registered Firebase app and the owner's
  Play creation form. Do not change it without an explicit owner decision. The developer display
  name, currently Nipun Rautela, is independent of the application ID.

## Solar timing and alarm correctness

- Calculate standard apparent sunrise/sunset from coordinates and local date
  using a documented, validated astronomical method. The Meeus-based baseline
  is implemented locally in `SolarCalculator.kt`. Tests compare it to
  independently generated Astronomy Engine 2.1.19 fixtures and a NOAA table value.
- Document horizon/refraction/elevation assumptions. Do not silently add ritual
  offsets or treat civil dawn/dusk as sunrise/sunset.
- Save each place's IANA time zone, calculate event instants in UTC, and display
  them in that place's zone. Indian places use `Asia/Kolkata`. Do not assume the
  phone's current zone belongs to every saved place.
- Preserve offline calculations and reminders after a place has been saved.
  City lookup may use connectivity; coordinate entry is the fallback.
- Validate against independent reference data with matching assumptions. Cover
  Indian seasonal/geographic variation, leap days, date boundaries, and missing
  rise/set events. Distinguish computed precision from observed physical accuracy.
- Handle notification and exact-alarm access explicitly. Start by evaluating
  user-granted `SCHEDULE_EXACT_ALARM`; do not assume restricted permissions will
  be approved or that all alarms deliver unconditionally.
- Replace/reschedule alarms after relevant place/settings changes, reboot,
  clock/time-zone changes, and permission changes. Make the alert location clear.
- Avoid constant background polling and per-second widget refreshes. Limit
  animated second-by-second countdowns to visible UI.

## Privacy, analytics, and content

- Firebase Analytics is selected only on its no-cost Spark plan. Do not enable
  paid exports or other billable services as part of ordinary setup.
- The intended audience is ages 13 and older; under-13 targeting was withdrawn.
  Do not add birth-date collection, age screening, or adult-only feature restrictions.
  Analytics is enabled by default with persistent opt-out in settings. Persist the
  user's choice and apply it before collecting subsequent
  events, including after restart. Opting out must not restrict app functionality.
- Disable advertising ID collection and ad personalization. Review automatic
  events/screen reporting as well as custom events.
- Never include coordinates, saved-place labels, mantra activity, religious
  practice completion, or arbitrary user text in custom analytics events.
- City/region reporting uses Firebase's IP-derived geography;
  do not send saved-place neighbourhoods or GPS coordinates as analytics parameters.
- Area names are separate saved-place metadata; keep Current location as the label.
  Preserve offline city choices and manual coordinates as alternatives to GPS/search.
- App-instance identifiers mean analytics is not fully anonymous. Keep privacy
  statements and Play Data safety answers aligned with actual SDK behavior.
- Crash reporting is a separate decision; do not add it implicitly with Analytics.
- The English/Hindi privacy policy is published through GitHub Pages at
  `https://nipunrautela.me/agnihotra/`, with its URL and owner-approved support
  contact in `integrations/ReleaseConfiguration.kt`. Approved mantra audio IDs
  remain empty; keep graceful fallbacks until recordings are supplied. Never
  fabricate service credentials,
  contact addresses, recordings, or successful backend connections.
- Keep credentials, signing keys, and machine-specific secrets out of Git and
  logs. External project configuration is not complete merely because code builds.
- Use Android resources for English/Hindi UI strings, plurals, and accessibility
  labels. Review Devanagari rendering and mantra text/translations for accuracy.

## Development and verification workflow

1. Inspect the working tree and relevant instructions. Make the smallest coherent
   change that completes the current request.
2. For UI work, check light/dark themes, Hindi, large text, contrast, touch targets,
   and accessible descriptions. Keep implementation details out of user flows.
3. Run checks appropriate to the change. Do not add tests that merely mirror
   implementation or run application tests for documentation-only edits.
4. Report the actual change, checks run, and any unresolved limitation. If a tool,
   SDK, emulator, or device is missing, state exactly what was not verified.

Typical PowerShell commands are:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

Run connected tests with an available emulator or authorized device. Unit tests
and UI fixtures use disposable app data. Prefer `tools/verify_device.ps1`, which
builds a separate `.verification` application ID and omits local Firebase/signing
files. The direct `connectedDebugAndroidTest` task can clear/uninstall its target;
do not run it against the owner's real installation without explaining that risk.
Use `tools/verify_device.ps1 -Release` to exercise optimized builds, including
`WidgetReleaseIntegrationTest`: debug-only rendering does not catch constructors
removed by R8. `-BuildOnly` creates disposable verification APKs without installing
them. Alarm device tests enable permissions only for the verification app through
Android Settings and briefly play actual alarm audio; keep the device idle.
Release verification defaults to the widget reflection smoke test and retains
test-runner Kotlin/tracing support only in the disposable target. Run the full
Compose/alarm suite with the default debug verification, then check the optimized
app in the actual launcher. Never interpret zero discovered tests as a pass.
Unit tests should cover solar reference values, next-event selection, date/time-zone
boundaries, reminder offsets, and active-place changes. UI tests should cover
manual setup/permission denial and the main English/Hindi flows. Use physical
devices for sound/vibration, lock screen, reboot, idle/battery saver, and OEM
battery restrictions; emulator results do not establish that behavior.

## Release context

The personal Play account was created on 2026-10-14. Plan for the applicable
closed-testing requirement, currently 12 testers continuously opted in for at
least 14 days followed by a production-access application; reverify at release.
The user intends to create the Play app after development. Keep implementation
work separate from publishing, external app registration, and signing-key handling
unless the current task authorizes those actions.
