#include "ee_ria_xroad_common_util_PasswordStore.h"

#include <stdlib.h>
#include <string.h>

#include "passwordstore.h"

JNIEXPORT jbyteArray JNICALL
Java_ee_ria_xroad_common_util_PasswordStore_read(JNIEnv *env, jclass jc,
        jstring j_pathname_for_ftok, jstring j_id)
{
    (void)jc;

    jbyteArray res = NULL;
    int error_code;
    signed char *ret_buf;
    const char *pathname_for_ftok = NULL;
    const char *id = NULL;
    char *ret_array = NULL;
    int ret_len = 0;

    if (j_pathname_for_ftok != NULL) {
        pathname_for_ftok = (*env)->GetStringUTFChars(
                env, j_pathname_for_ftok, NULL);
    }

    if (j_id != NULL) {
        id = (*env)->GetStringUTFChars(env, j_id, NULL);
    }

    error_code = LEGACY_passwordRead(pathname_for_ftok, id,
            &ret_array, &ret_len);

    if (error_code != 0) {
        goto error;
    }

    if (ret_array == NULL) {
        res = NULL;
    } else {
        // Create new Java byte array with the correct length
        res = (*env)->NewByteArray(env, ret_len);
        if (res == NULL) {
            // Out of memory.
            error_code = MALLOC_FAILURE;
            free(ret_array);
            goto error;
        }
        ret_buf = (*env)->GetByteArrayElements(env, res, NULL);

        // Copy answer to Java array.
        memcpy(ret_buf, ret_array, ret_len);

        (*env)->ReleaseByteArrayElements(env, res, ret_buf, 0);
    }

error:
    if (j_pathname_for_ftok != NULL) {
        (*env)->ReleaseStringUTFChars(env, j_pathname_for_ftok,
                pathname_for_ftok);
    }
    if (j_id != NULL) {
        (*env)->ReleaseStringUTFChars(env, j_id, id);
    }

    free(ret_array);

    if (error_code == 0) {
        return res;
    } else {
        (*env)->ThrowNew(env,
                (*env)->FindClass(env, "java/lang/RuntimeException"),
                LEGACY_strError(error_code));
    }

    return res; // For suppress warning.
}


JNIEXPORT void JNICALL
Java_ee_ria_xroad_common_util_PasswordStore_write(JNIEnv *env, jclass jc,
        jstring j_pathname_for_ftok, jstring j_id,
        jbyteArray j_password, jint permissions)
{
    (void)jc;

    int error_code;
    const char *pathname_for_ftok = NULL;
    const char *id = NULL;
    signed char *password = NULL;
    int password_len = 0;

    if (j_pathname_for_ftok != NULL) {
        pathname_for_ftok = (*env)->GetStringUTFChars(
                env, j_pathname_for_ftok, NULL);
    }

    if (j_id != NULL) {
        id = (*env)->GetStringUTFChars(env, j_id, NULL);
    }

    if (j_password != NULL) {
        password = (*env)->GetByteArrayElements(env, j_password, NULL);
        password_len = (*env)->GetArrayLength(env, j_password);
    }

    error_code = LEGACY_passwordWrite(pathname_for_ftok, id,
            (char *) password, password_len, permissions);
    if (error_code != 0) {
        goto error;
    }

    if (j_id != NULL) {
        (*env)->ReleaseStringUTFChars(env, j_id, id);
    }
    if (j_pathname_for_ftok != NULL) {
        (*env)->ReleaseStringUTFChars(env, j_pathname_for_ftok, pathname_for_ftok);
    }
    if (j_password != NULL) {
        (*env)->ReleaseByteArrayElements(env, j_password, password, 0);
    }

    return;

error:
    if (j_id != NULL) {
        (*env)->ReleaseStringUTFChars(env, j_id, id);
    }
    if (j_pathname_for_ftok != NULL) {
        (*env)->ReleaseStringUTFChars(env, j_pathname_for_ftok, pathname_for_ftok);
    }
    if (j_password != NULL) {
        (*env)->ReleaseByteArrayElements(env, j_password, password, 0);
    }

    (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/lang/RuntimeException"),
            LEGACY_strError(error_code));
}

JNIEXPORT void JNICALL
Java_ee_ria_xroad_common_util_PasswordStore_clear(JNIEnv *env, jclass jc,
        jstring j_pathname_for_ftok, jint permissions)
{
    (void)jc;

    int error_code;
    const char *pathname_for_ftok = NULL;

    if (j_pathname_for_ftok != NULL) {
        pathname_for_ftok = (*env)->GetStringUTFChars(
                env, j_pathname_for_ftok, NULL);
    }

    error_code = LEGACY_passwordClear(pathname_for_ftok, permissions);
    if (error_code != 0) {
        goto error;
    }

    if (j_pathname_for_ftok != NULL) {
        (*env)->ReleaseStringUTFChars(env, j_pathname_for_ftok, pathname_for_ftok);
    }

    return;

error:
    if (j_pathname_for_ftok != NULL) {
        (*env)->ReleaseStringUTFChars(env, j_pathname_for_ftok, pathname_for_ftok);
    }

    (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/lang/RuntimeException"),
            LEGACY_strError(error_code));
}

