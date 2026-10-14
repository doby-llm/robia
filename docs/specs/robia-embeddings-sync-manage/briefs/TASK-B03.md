# TASK-B03 — Application container

## Goal
Sync (WorkManager, S04) and the model engine (E01) must live for the whole app process, not only while `MainActivity` exists. Create a `RobiaApplication` that owns the singletons. Behaviour stays identical.

## Read first
`MainActivity.kt` (all of `onCreate`; the `settingsDataStore` delegate at file level), `AndroidManifest.xml`.

## Steps
1. New file `…/RobiaApplication.kt`:
   ```kotlin
   class RobiaApplication : Application() {
       val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
       val database by lazy { RobiaDatabase.getInstance(this) }
       val settingsRepository by lazy { /* same constructor as in MainActivity, using settingsDataStore */ }
       val wardrobeRepository by lazy { LocalWardrobeRepository(database.wardrobeDao()) }
       val tagRepository by lazy { LocalTagRepository(database.tagDao(), database.syncTombstoneDao()) }
       val syncSnapshotRepository by lazy { /* same as MainActivity */ }
       val restoreSyncLogRepository by lazy { /* same as MainActivity */ }
       val syncGateway: WardrobeSyncOutboxProcessor by lazy { /* same args as MainActivity, scope = appScope,
           Drive repository built with applicationContext and Identity.getAuthorizationClient(this) */ }
   }
   ```
   Copy constructor arguments exactly from `MainActivity.onCreate`; only `scope` changes from `lifecycleScope` to `appScope`.
2. Move `private val Context.settingsDataStore by preferencesDataStore(name = "robia_settings")` from `MainActivity.kt` to `RobiaApplication.kt` (make it `internal`). The name must stay `robia_settings` (existing user data).
3. `MainActivity`: `val app = application as RobiaApplication` and use `app.…` everywhere instead of local construction. Keep the authorization launcher and `requestGoogleDriveAuthorization` in the activity.
4. If the processor has a start/initialize call in `onCreate` (e.g. `start()` / `onAppStarted()`), keep calling it from the activity's `onCreate` exactly as today (it must be idempotent; if it is not, guard it with an `AtomicBoolean` in the application).
5. Manifest: `<application android:name=".RobiaApplication" …>`.
6. `app/build.gradle.kts`: `implementation(libs.androidx.lifecycle.process)` (used in E01; adding now avoids a second build edit).

## Tests
Existing tests only.

## Done when
CI green (V-B05); diff shows `MainActivity` no longer constructs repositories or the processor.

## Do not
Change any repository/processor logic.
