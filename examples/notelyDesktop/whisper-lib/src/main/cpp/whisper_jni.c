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
// "_00024Companion_" is JNI's mangling of "$Companion" (Kotlin companion object).

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    const char *path = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, whisper_context_default_params());
    (*env)->ReleaseStringUTFChars(env, model_path_str, path);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env); UNUSED(thiz);
    whisper_free((struct whisper_context *) context_ptr);
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads,
        jfloatArray audio_data, jstring language_str, jstring prompt_str) {
    UNUSED(thiz);
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    jfloat *audio = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize len = (*env)->GetArrayLength(env, audio_data);

    const char *language = (*env)->GetStringUTFChars(env, language_str, NULL);
    const char *prompt   = (*env)->GetStringUTFChars(env, prompt_str,    NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = false;
    params.language         = language;
    params.initial_prompt   = (prompt && prompt[0] != '\0') ? prompt : NULL;
    params.n_threads        = num_threads;
    params.offset_ms        = 0;
    params.no_context       = true;
    params.single_segment   = false;

    whisper_reset_timings(ctx);
    if (whisper_full(ctx, params, audio, len) != 0) {
        fprintf(stderr, "whisper_full failed\n");
    }

    (*env)->ReleaseStringUTFChars(env, language_str, language);
    (*env)->ReleaseStringUTFChars(env, prompt_str,   prompt);
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env); UNUSED(thiz);
    return whisper_full_n_segments((struct whisper_context *) context_ptr);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    const char *text = whisper_full_get_segment_text((struct whisper_context *) context_ptr, index);
    return (*env)->NewStringUTF(env, text);
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentT0(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env); UNUSED(thiz);
    return whisper_full_get_segment_t0((struct whisper_context *) context_ptr, index);
}

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentT1(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env); UNUSED(thiz);
    return whisper_full_get_segment_t1((struct whisper_context *) context_ptr, index);
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject thiz) {
    UNUSED(thiz);
    return (*env)->NewStringUTF(env, whisper_print_system_info());
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_benchMemcpy(
        JNIEnv *env, jobject thiz, jint n_threads) {
    UNUSED(thiz);
    return (*env)->NewStringUTF(env, whisper_bench_memcpy_str(n_threads));
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_benchGgmlMulMat(
        JNIEnv *env, jobject thiz, jint n_threads) {
    UNUSED(thiz);
    return (*env)->NewStringUTF(env, whisper_bench_ggml_mul_mat_str(n_threads));
}
