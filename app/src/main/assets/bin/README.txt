Place ARM64 static binaries here before building:

  secilc             - SELinux CIL compiler (from AOSP build or prebuilt)
  sepolicy-analyze   - SELinux policy analysis tool (from AOSP build or prebuilt)

Both must be:
  - ARM64 architecture (aarch64)
  - Statically linked (no dependency on device libc)
  - Named exactly as above (no extension)

Build from AOSP source:
  make secilc sepolicy-analyze
  find out/ -name "secilc" -o -name "sepolicy-analyze" | grep "android_arm64"

These files are NOT committed to git (see .gitignore).
GitHub Actions workflow must download or build them before the APK build step.
