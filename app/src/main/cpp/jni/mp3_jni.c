/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * JNI bridge to LAME (lame/, LGPL-2.0-or-later) for saving songs as MP3.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include "lame/lame.h"

static void set_text(JNIEnv *env, lame_t lame, jstring value, void (*setter)(lame_t, const char *)) {
    if (value == NULL) return;
    const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
    if (chars != NULL) {
        setter(lame, chars);
        (*env)->ReleaseStringUTFChars(env, value, chars);
    }
}

JNIEXPORT jlong JNICALL
Java_com_ozyern_exhale_export_Mp3Encoder_nativeInit(JNIEnv *env, jclass clazz, jint sampleRate, jint channels,
                                                    jint kbps, jstring title, jstring artist, jstring album,
                                                    jstring year, jbyteArray cover) {
    lame_t lame = lame_init();
    if (lame == NULL) return 0;
    lame_set_in_samplerate(lame, sampleRate);
    lame_set_out_samplerate(lame, sampleRate);
    lame_set_num_channels(lame, channels);
    lame_set_mode(lame, channels == 1 ? MONO : JOINT_STEREO);
    lame_set_VBR(lame, vbr_off);
    lame_set_brate(lame, kbps);
    lame_set_quality(lame, 2);

    id3tag_init(lame);
    id3tag_add_v2(lame);
    set_text(env, lame, title, (void (*)(lame_t, const char *)) id3tag_set_title);
    set_text(env, lame, artist, (void (*)(lame_t, const char *)) id3tag_set_artist);
    set_text(env, lame, album, (void (*)(lame_t, const char *)) id3tag_set_album);
    set_text(env, lame, year, (void (*)(lame_t, const char *)) id3tag_set_year);
    if (cover != NULL) {
        jsize size = (*env)->GetArrayLength(env, cover);
        jbyte *bytes = (*env)->GetByteArrayElements(env, cover, NULL);
        if (bytes != NULL) {
            id3tag_set_albumart(lame, (const char *) bytes, (size_t) size);
            (*env)->ReleaseByteArrayElements(env, cover, bytes, JNI_ABORT);
        }
    }

    if (lame_init_params(lame) < 0) {
        lame_close(lame);
        return 0;
    }
    return (jlong) (intptr_t) lame;
}

JNIEXPORT jbyteArray JNICALL
Java_com_ozyern_exhale_export_Mp3Encoder_nativeEncode(JNIEnv *env, jclass clazz, jlong handle, jshortArray pcm,
                                                      jint frames) {
    lame_t lame = (lame_t) (intptr_t) handle;
    int capacity = (int) (1.25 * frames + 7200);
    unsigned char *out = (unsigned char *) malloc((size_t) capacity);
    if (out == NULL) return NULL;
    jshort *samples = (*env)->GetShortArrayElements(env, pcm, NULL);
    int written;
    if (lame_get_num_channels(lame) == 2) {
        written = lame_encode_buffer_interleaved(lame, samples, frames, out, capacity);
    } else {
        written = lame_encode_buffer(lame, samples, samples, frames, out, capacity);
    }
    (*env)->ReleaseShortArrayElements(env, pcm, samples, JNI_ABORT);
    if (written < 0) {
        free(out);
        return NULL;
    }
    jbyteArray result = (*env)->NewByteArray(env, written);
    (*env)->SetByteArrayRegion(env, result, 0, written, (const jbyte *) out);
    free(out);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_ozyern_exhale_export_Mp3Encoder_nativeFlush(JNIEnv *env, jclass clazz, jlong handle) {
    lame_t lame = (lame_t) (intptr_t) handle;
    unsigned char out[7200];
    int written = lame_encode_flush(lame, out, sizeof(out));
    if (written < 0) written = 0;
    jbyteArray result = (*env)->NewByteArray(env, written);
    (*env)->SetByteArrayRegion(env, result, 0, written, (const jbyte *) out);
    return result;
}

JNIEXPORT void JNICALL
Java_com_ozyern_exhale_export_Mp3Encoder_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
    lame_t lame = (lame_t) (intptr_t) handle;
    if (lame != NULL) lame_close(lame);
}
