# Reetech RT9-BM5 / RC9-BM5 test notes (v1.5.6)

- User-reported observation: Xiaomi 17's Mi Remote controls this Reetech unit for power, temperature, and other controls when the Mi Remote brand preset is set to Midea.
- This version therefore routes Reetech selections through the app's existing Midea 38 kHz encoder as a compatibility trial.
- This is an evidence-based hypothesis, not a verified capture of the Xiaomi Mi Remote waveform. Midea protocol variants exist, so the app's command may still fail.
- The status message says that Android completed an IR transmit call; it does not claim the AC received the command.
- No APK is included. This archive does not contain Gradle wrapper scripts and the current environment has no Gradle installation. Build using Android Studio or add a Gradle wrapper / GitHub Actions workflow before running CI.
