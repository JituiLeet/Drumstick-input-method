# Third-party runtime staging

This directory is intentionally empty in the source archive.

For a production Android Rime build, place the ABI-specific `liblibrime.so` and its required runtime libraries here or copy them to `app/src/main/jniLibs/<abi>/` during CI. The Java frontend does not hard-link to a particular vendor build; `RimeNative` probes `liblibrime.so` dynamically.

A useful Android Rime build reference is the Trime project and its GitHub Actions native cache strategy. Verify licenses and ABI/API compatibility before redistributing any native binary.
