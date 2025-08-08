# VSS Rust Client FFI Module

This module contains the uniffi bindings from [vss-rust-client-ffi](https://github.com/synonymdev/vss-rust-client-ffi).

## Notes

- **DO NOT** manually edit the content of the files in this module - they are auto-generated
- Detekt is disabled for this module in `build.gradle.kts`

## Updating

When updating the with new binding:
1. Replace the `vss_rust_client_ffi.kt` with the one from `bidnings/android`
2. Overwrite the entire `jniLibs` directory and its contents with the one from `bidnings/android`. 
3. Ensure the module builds successfully by rebuilding & running the app.
