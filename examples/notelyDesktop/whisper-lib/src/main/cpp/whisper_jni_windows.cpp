#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "whisper.h"

#define UNUSED(x) (void)(x)

// JNI symbol names encode the full Kotlin class path:
//   package com.whispercpp.whisper
//   class WhisperLib { companion object { external fun ... } }
//
// "_00024Companion_" is JNI's mangling of "$Companion" — it appears because
// these functions live inside a Kotlin `companion object`, not a plain `object`.

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    const char *path = env->GetStringUTFChars(model_path_str, nullptr);
    whisper_context *ctx = whisper_init_from_file_with_params(path, whisper_context_default_params());
    env->ReleaseStringUTFChars(model_path_str, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env); UNUSED(thiz);
    whisper_free(reinterpret_cast<whisper_context *>(context_ptr));
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads, jfloatArray audio_data) {
    UNUSED(thiz);
    auto *ctx = reinterpret_cast<whisper_context *>(context_ptr);
    jfloat *audio = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize len = env->GetArrayLength(audio_data);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = false;
    params.language         = "en";
    params.n_threads        = num_threads;
    params.offset_ms        = 0;
    params.no_context       = true;
    params.single_segment   = false;

    whisper_reset_timings(ctx);
    if (whisper_full(ctx, params, audio, len) != 0) {
        fprintf(stderr, "whisper_full failed\n");
    } else {
        whisper_print_timings(ctx);
    }
    env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env); UNUSED(thiz);
    return whisper_full_n_segments(reinterpret_cast<whisper_context *>(context_ptr));
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    const char *text = whisper_full_get_segment_text(
            reinterpret_cast<whisper_context *>(context_ptr), index);
    return env->NewStringUTF(text);
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentT0(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env); UNUSED(thiz);
    return whisper_full_get_segment_t0(reinterpret_cast<whisper_context *>(context_ptr), index);
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentT1(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env); UNUSED(thiz);
    return whisper_full_get_segment_t1(reinterpret_cast<whisper_context *>(context_ptr), index);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject thiz) {
    UNUSED(thiz);
    return env->NewStringUTF(whisper_print_system_info());
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_benchMemcpy(
        JNIEnv *env, jobject thiz, jint n_threads) {
    UNUSED(thiz);
    return env->NewStringUTF(whisper_bench_memcpy_str(n_threads));
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_benchGgmlMulMat(
        JNIEnv *env, jobject thiz, jint n_threads) {
    UNUSED(thiz);
    return env->NewStringUTF(whisper_bench_ggml_mul_mat_str(n_threads));
}

} // extern "C"
