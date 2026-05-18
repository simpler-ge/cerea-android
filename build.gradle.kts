// Root build script. Plugin versions are declared here once and applied with
// `apply false`; each module then opts in via its own `plugins {}` block.
plugins {
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
