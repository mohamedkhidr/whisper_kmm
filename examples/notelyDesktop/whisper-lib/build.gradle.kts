plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.kotlinx.coroutinesCore)
}

// ── Paths ─────────────────────────────────────────────────────────────────────

val nativeSrcDir    = projectDir.resolve("src/main/cpp")
val cmakeBuildDir   = layout.buildDirectory.dir("cmake").get().asFile
val nativeOutputDir = File(cmakeBuildDir, "output")
val generatedResDir = layout.buildDirectory.dir("generated/natives").get().asFile

// ── CMake configure ───────────────────────────────────────────────────────────

val configureNative by tasks.registering(Exec::class) {
    group       = "native"
    description = "CMake configure: generate build files for whisper_jni"

    notCompatibleWithConfigurationCache("cmake exec task")

    inputs.file(nativeSrcDir.resolve("CMakeLists.txt"))
    outputs.dir(cmakeBuildDir)

    val buildDir    = cmakeBuildDir
    val srcDir      = nativeSrcDir
    // 2 levels up from notelyDesktop/ reaches whisper.cpp/ repo root
    val whisperRoot = rootProject.projectDir.resolve("../..").canonicalPath

    doFirst { buildDir.mkdirs() }

    workingDir = cmakeBuildDir
    commandLine(
        "cmake", srcDir.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DWHISPER_LIB_DIR=$whisperRoot"
    )
}

// ── CMake build ───────────────────────────────────────────────────────────────

val buildNative by tasks.registering(Exec::class) {
    group       = "native"
    description = "CMake build: compile whisper_jni.dll / libwhisper_jni.dylib"

    notCompatibleWithConfigurationCache("cmake build exec task")
    dependsOn(configureNative)

    inputs.dir(nativeSrcDir)
    outputs.dir(nativeOutputDir)

    workingDir = cmakeBuildDir
    commandLine("cmake", "--build", ".", "--config", "Release")
}

// ── Bundle into JAR resources ─────────────────────────────────────────────────

val bundleNativeLibrary by tasks.registering(Copy::class) {
    group       = "native"
    description = "Bundle whisper_jni native library into module JAR resources"

    dependsOn(buildNative)
    from(fileTree(nativeOutputDir) {
        include("whisper_jni.dll", "libwhisper_jni.so", "libwhisper_jni.dylib")
    })
    into(generatedResDir.resolve("natives"))
}

sourceSets {
    main {
        resources.srcDir(generatedResDir)
    }
}

tasks.named("processResources") { dependsOn(bundleNativeLibrary) }
