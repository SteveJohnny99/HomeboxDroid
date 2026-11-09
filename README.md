# HomeboxDroid

App Android nativa (Kotlin + Jetpack Compose) per gestire un server
[Homebox](https://homebox.software/) self-hosted. Vedi `instructions.md` per il
progetto completo (obiettivo, API, decisioni, milestone).

Stato: M0 - scheletro. Compila e produce un APK; il login non e' ancora
funzionante (arriva in M1).

## Requisiti
- Android Studio (ultima stabile) oppure JDK 17 + Android SDK (compileSdk 35).
- minSdk 26 (Android 8.0).

## Build locale
    ./gradlew assembleDebug
L'APK esce in `app/build/outputs/apk/debug/app-debug.apk`.
Aprendo il progetto in Android Studio il wrapper e l'SDK vengono gestiti in
automatico; `local.properties` con il path dell'SDK viene creato da Studio e non
va committato.

## Firma
La build e' firmata con una chiave di debug committata in `keystore/debug.keystore`
(password standard `android`). Serve a far si' che ogni build - locale o CI -
usi la stessa chiave, cosi' gli aggiornamenti si installano sopra il precedente.
Non e' una chiave di release: va sostituita se un giorno si pubblica davvero.

## CI / APK pronto da scaricare
Workflow in `.github/workflows/build.yml` (GitHub Actions):
- A ogni push/PR su `main` (o `master`) builda l'APK di debug e lo carica come
  artifact. Lo scarichi da: scheda Actions del repo -> il run -> sezione
  Artifacts -> `HomeboxDroid-debug-apk` (zip con dentro l'APK).
- Puoi lanciarlo a mano da Actions ("Run workflow", workflow_dispatch).
- Se crei un tag `vX.Y.Z` e lo pushi, oltre all'artifact viene creata una
  GitHub Release con l'APK allegato direttamente (scaricabile senza unzip).

Per installare sul telefono: abilitare "installa da sorgenti sconosciute" la
prima volta, poi aprire l'APK.

## Struttura
    app/src/main/java/it/stevej/homeboxdroid/
      HomeboxApp.kt          Application (Hilt)
      MainActivity.kt        entry point Compose
      ui/theme/              tema Material3 (palette morbida)
      ui/onboarding/         schermata di onboarding (M0, solo UI)
      ui/navigation/         destinazioni (placeholder)
      di/                    moduli Hilt
      data/ domain/          livelli dati e dominio (da popolare in M1/M2)

## Stack
Compose + Material3, Hilt, Navigation Compose, Retrofit + OkHttp +
kotlinx.serialization, DataStore + security-crypto, Room (cache),
Coil, CameraX + ML Kit (scansione), WorkManager. Versioni centralizzate in
`gradle/libs.versions.toml`.
