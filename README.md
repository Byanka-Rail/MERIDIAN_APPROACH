# MERIDIAN APPROACH v0.29.84 Android wrapper

This project packages the current single-file MERIDIAN APPROACH HTML as an offline Android WebView app.

## Included behavior

- Game HTML is bundled at `app/src/main/assets/MERIDIAN_APPROACH.html`.
- No Internet permission is requested.
- Hardware-accelerated WebView is enabled for Three.js/WebGL.
- Immersive full-screen mode and screen-on are enabled.
- Rotation does not recreate the Activity, reducing accidental game reloads.
- HTML file chooser is bridged to Android's document picker for JSON import.
- Both portrait and landscape are supported (`fullSensor`). Landscape is built for two-thumb flying: full-width windshield, instrument strip on top, everything below it is the flight-control zone.

## Build with GitHub Actions

1. Create a GitHub repository.
2. Upload the **contents** of this project folder to the repository root.
3. Open **Actions** → **Build MERIDIAN APPROACH APK**.
4. Run the workflow, or push to `main`/`master`.
5. Download the artifact named `MERIDIAN_APPROACH_v0.29.84_ANDROID`.
6. Install `MERIDIAN_APPROACH_v0.29.84_DEBUG.apk` on Android.

The debug APK is automatically signed with the standard debug signing key and is directly installable for testing.

## Local Android Studio / command line

The project uses Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17, and compileSdk 35.
Run:

    gradle --no-daemon assembleDebug

Result:

    app/build/outputs/apk/debug/app-debug.apk

## Updating only the game later

Replace:

    app/src/main/assets/MERIDIAN_APPROACH.html

with the newer HTML, then update `versionCode` and `versionName` in `app/build.gradle` and rebuild. The Android wrapper itself does not need to be rewritten.


## v0.29.80 SAFE FIX
- Android native shell is byte-for-byte the known-launching v0.29.78 MainActivity.
- Removed only the 3 stray literal `\n` tokens that rendered at the top in WebView.
- AUTOFLIGHT manual collapse now overrides routine gear/flap reminders; emergency states may reopen it.
- The v0.29.79 edge-to-edge/cutout and renderer-recreate changes are intentionally removed.


## v0.29.81 - v0.29.84 QA FIX
HTML (game):
- ILS glide path now passes the threshold at TCH (50 ft); the beam ground point sits tch/tan(GS) past the threshold. Autoland used to follow the beam into the ground 1 m short of the threshold (TERRAIN CONTACT on MRDN 36C).
- AP flare rewritten: flare at 40 ft RA, V/S schedule -400 → -220 fpm, higher pitch authority, and it now works with a single AP as well as AP1+AP2. Regression: MRDN/MRCP/MRVL/MRHB/MRSK FINAL 10 & FINAL 5 autoland touch down 430-475 m past the threshold at -90 to -110 fpm.
- START no longer reverts an OPERATION / START LOCATION you picked under ADVANCED back to the scenario default (LANDING used to start as FULL ARRIVAL at 77 NM).
- ASSIST card raised above the floating flight-control zone so ARM APPR / 넘기기 can be tapped during manual flight on phones.
- Radio altimeter and terrain-contact check clamp terrain at sea level (seabed outside the region mask read as -900 to -1600 m).
- Airport JSON import: every existing airport ID is protected (was only 3 of 46); runway length/width/glide and elevation must be finite numbers in range.
- Phone landscape (v0.29.83): built for two-thumb flying. `classifyDual210` sorts the two pointers inside the flight-control zone by x and gives roll to the left one and pitch to the right one, so the zone must span the full screen width - a deck docked on the right takes the right thumb out of the control area. Landscape under 620 px tall puts the windshield full-screen, floats a deck strip over the top (PFD/ND, THR, GO AROUND), and gives everything below the strip to the flight-control zone. The rest of the deck (FCU, AP, FLAP/GEAR, overhead, yoke) opens as a scrollable drawer from the DECK button at the strip right edge. Verified at 915x412, 844x390 and 740x360: the zone spans the full width, 21/21 sampled points hit it at 740x360, and a simultaneous two-pointer drag moves stickX and stickY together. The ASSIST card sits at the top of the zone so it never takes thumb area.
- Landscape strip now also carries FLAP and GEAR (v0.29.84), next to THR and GO AROUND, so an approach can be flown without opening the drawer. All four live in the deck already; the strip just surfaces them with CSS.
- Quick Play (v0.29.84): the green button on the start and finish screens picks a random airport, weather and time of day and drops you on a 5 NM final in LANDING configuration. It validates the pick first - some fields (IS01) have terrain on the final approach course, and the terrain guard runs before the map finishes loading, so those spawn either inside the hill or 6,000 ft above the path. Quick Play measures the 3-degree path clearance over terrain from 1 to 6.5 NM and re-picks (up to 3 tries, then falls back to MRDN) if it is under 150 ft.
- v0.29.82 first tried a two-column landscape (windshield left, deck right); that restored the view but broke two-handed control, so it was replaced.
- ASSIST card passes drags through to the flight-control zone (only its two buttons take taps), so raising it above the zone did not cost stick area.
- Top button row no longer overflows the right screen edge.
- `resize` handler bound to the live drawAll (wrapper chain), version strings unified, LANDING/APPROACH self-tests rebaselined to the current start profiles.

Android shell:
- Both orientations (`fullSensor`); the HTML handles the landscape layout.
- `onRenderProcessGone` recreates the Activity instead of letting the whole app die when WebView's renderer is killed under memory pressure.
- Back button requires a second press within 2 s to quit (toast), so a stray press no longer ends a flight.
- versionCode 2984 / versionName 0.29.84.
