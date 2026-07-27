// Intentionally empty.
//
// Declaring plugins here with `apply false` would put them on the build
// classpath for every module, which makes Gradle reject any module that then
// requests the same plugin with a version. Plugin versions are pinned in
// settings.gradle.kts instead; modules request them with a bare `id("...")`.
