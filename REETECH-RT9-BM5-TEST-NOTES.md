# Reetech RT9-BM5 / RC9-BM5 test notes (v1.5.7)

- User-reported observation: Xiaomi 17's Mi Remote controls this Reetech unit for power, temperature, and other controls when the preset is set to Midea.
- v1.5.6 used the standard Midea state encoder and did not operate the user's AC.
- v1.5.7 adds a separate Reetech trial profile that sets the otherwise-unknown OEM bit in the Midea byte-4 state, based on the public IRremoteESP8266 documentation that some OEM units require this bit.
- This is a targeted protocol hypothesis, not a verified capture of Mi Remote's waveform or proof of Reetech RT9-BM5 compatibility. Do not describe it as confirmed working until tested on the AC.
- The Android status message distinguishes a completed transmit API call from confirmed AC reception.
- No APK is included. Use GitHub Actions to build the debug APK; workflow metadata checks are updated to versionName 1.5.7 / versionCode 25.


## v1.5.12 note
The Android APK now bundles the same control UI as the web version, including the carrier-frequency selector. Select Reetech, choose 36/38/40/56 kHz, then press power to test. The chosen carrier is only a test variable; the Reetech encoder remains unverified.
