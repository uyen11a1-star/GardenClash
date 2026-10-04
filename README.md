# Garden Clash — Android MVP

Original 2D lane-defense / arena prototype designed to run well on low-end Android phones.

## Build in GitHub Actions

The repository is intentionally wrapper-free so GitHub Actions can install Gradle 9.6 directly. The workflow builds with JDK 17, Android SDK 36 and uploads the APK as a workflow artifact.

## Local / Termux idea

```bash
git clone <YOUR_REPO_URL>
cd garden-clash-mvp
# GitHub Actions performs the APK build. Termux is used for editing + git.
git add .
git commit -m "Build Garden Clash MVP"
git push -u origin main
```

## Game loop

- 5 lanes × 9 columns
- Tap a plant card, then tap a tile to place it
- Sun resource, enemy waves, projectiles and HP bars
- Three defensive lives
- Score → Crown progression for an Arena-like loop
- All artwork is drawn in code so the MVP has no external copyrighted assets

## Important

This is an original prototype and should not copy Plants vs. Zombies characters, names, audio or artwork.
