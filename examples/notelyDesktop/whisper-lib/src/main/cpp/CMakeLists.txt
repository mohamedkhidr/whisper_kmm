cmake_minimum_required(VERSION 3.10)
project(whisper_jni)

# This project lives at whisper.cpp/examples/notelyDesktop/whisper-lib/src/main/cpp/
# so the whisper.cpp repo root is 6 directories above CMAKE_SOURCE_DIR.
# Override if your layout differs: cmake ... -DWHISPER_LIB_DIR=C:/dev/whisper.cpp
get_filename_component(_whisper_root "${CMAKE_SOURCE_DIR}/../../../../../../" ABSOLUTE)
set(WHISPER_LIB_DIR "${_whisper_root}" CACHE PATH "Path to whisper.cpp source" FORCE)

# Locate JNI headers via JAVA_HOME
find_package(JNI REQUIRED)

# ── Pull in whisper.cpp's own build system ────────────────────────────────────
# This mirrors the Android approach where the NDK build pulls in whisper's
# CMakeLists.txt so all defines (WHISPER_VERSION, etc.) are set correctly.
# We turn off everything we don't need to keep build time short.
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)  # build whisper as static → links into our DLL

add_subdirectory(${WHISPER_LIB_DIR} ${CMAKE_BINARY_DIR}/whisper_build EXCLUDE_FROM_ALL)

# ── Our JNI shared library ────────────────────────────────────────────────────
add_library(whisper_jni SHARED ${CMAKE_SOURCE_DIR}/whisper_jni.c)

target_include_directories(whisper_jni PRIVATE
        ${WHISPER_LIB_DIR}/include
        ${JNI_INCLUDE_DIRS}
)

# Link against the whisper static lib built above — it pulls in ggml transitively
target_link_libraries(whisper_jni PRIVATE whisper)

# Flatten output: DLL always lands in output/ regardless of VS multi-config
set_target_properties(whisper_jni PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY           "${CMAKE_BINARY_DIR}/output"
        RUNTIME_OUTPUT_DIRECTORY_RELEASE   "${CMAKE_BINARY_DIR}/output"
        RUNTIME_OUTPUT_DIRECTORY_DEBUG     "${CMAKE_BINARY_DIR}/output"
        LIBRARY_OUTPUT_DIRECTORY           "${CMAKE_BINARY_DIR}/output"
        LIBRARY_OUTPUT_DIRECTORY_RELEASE   "${CMAKE_BINARY_DIR}/output"
        LIBRARY_OUTPUT_DIRECTORY_DEBUG     "${CMAKE_BINARY_DIR}/output"
)
