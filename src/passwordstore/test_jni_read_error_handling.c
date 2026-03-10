/**
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "passwordstore.h"   // for MALLOC_FAILURE (and error codes)

// We call the real JNI function from ee_ria_xroad_common_util_MemoryPasswordStoreProvider.c
JNIEXPORT jbyteArray JNICALL
Java_ee_ria_xroad_common_util_MemoryPasswordStoreProvider_read(
    JNIEnv *env, jclass jc, jstring j_pathname_for_ftok, jstring j_id);

// ------------------------------------------------------------------
// Stub out native library API so this test is deterministic and local
// ------------------------------------------------------------------
// Force a successful read returning a heap buffer, so JNI code will
// definitely have something to free on the error path.
int LEGACY_passwordRead(const char *pathname_for_ftok, const char *password_id,
                        char **ret_buf, int *ret_buf_len)
{
    (void)pathname_for_ftok;
    (void)password_id;

    const char *secret = "secret123";
    size_t n = strlen(secret);

    *ret_buf = (char *)malloc(n);
    if (*ret_buf == NULL) {
        *ret_buf_len = 0;
        return MALLOC_FAILURE;
    }

    memcpy(*ret_buf, secret, n);
    *ret_buf_len = (int)n;

    return 0; // success
}

// Needed because the provider C file also defines JNI write/clear which
// reference these symbols. We don't call them here; they exist only to link.
int LEGACY_passwordWrite(const char *pathname_for_ftok, const char *password_id,
                         const char *password, int password_length, int permissions)
{
    (void)pathname_for_ftok;
    (void)password_id;
    (void)password;
    (void)password_length;
    (void)permissions;
    return 0;
}

int LEGACY_passwordClear(const char *pathname_for_ftok, int permissions)
{
    (void)pathname_for_ftok;
    (void)permissions;
    return 0;
}

// Minimal error string helper used by JNI code on ThrowNew
const char* LEGACY_strError(int error_code)
{
    switch (error_code) {
        case MALLOC_FAILURE:
            return "Malloc failure";
        default:
            return "UNKNOWN";
    }
}

// ----------------------------
// free() wrapper to detect double free
// ----------------------------
static void *first_freed_ptr = NULL;
static int double_free_detected = 0;

void __real_free(void *p);
void __wrap_free(void *p)
{
    if (p != NULL) {
        if (first_freed_ptr == p) {
            double_free_detected = 1;
            // Don't actually free twice
            return;
        }
        if (first_freed_ptr == NULL) {
            first_freed_ptr = p;
        }
    }
    __real_free(p);
}

// ----------------------------
// Minimal fake JNIEnv
// ----------------------------
// We only implement the JNI calls that your provider's read() uses
// along the "NewByteArray returns NULL" (OOM) failure path.
static const char* fake_GetStringUTFChars(JNIEnv *env, jstring str, jboolean *isCopy)
{
    (void)env;
    (void)str;
    if (isCopy) *isCopy = JNI_FALSE;
    return "/"; // any non-null pathname is fine
}

static void fake_ReleaseStringUTFChars(JNIEnv *env, jstring str, const char *chars)
{
    (void)env;
    (void)str;
    (void)chars;
}

static jbyteArray fake_NewByteArray(JNIEnv *env, jsize len)
{
    (void)env;
    (void)len;
    // Force OOM branch deterministically
    return NULL;
}

static jclass fake_FindClass(JNIEnv *env, const char *name)
{
    (void)env;
    (void)name;
    return (jclass)0x1; // dummy non-null
}

static jint fake_ThrowNew(JNIEnv *env, jclass clazz, const char *msg)
{
    (void)env;
    (void)clazz;
    (void)msg;
    return 0;
}

static struct JNINativeInterface_ fake_table;
static JNIEnv fake_env = (JNIEnv)&fake_table;

int main(void)
{
    memset(&fake_table, 0, sizeof(fake_table));
    fake_table.GetStringUTFChars = fake_GetStringUTFChars;
    fake_table.ReleaseStringUTFChars = fake_ReleaseStringUTFChars;
    fake_table.NewByteArray = fake_NewByteArray;
    fake_table.FindClass = fake_FindClass;
    fake_table.ThrowNew = fake_ThrowNew;

    // Reset detection state
    first_freed_ptr = NULL;
    double_free_detected = 0;

    // jstring values are not dereferenced by our fake functions
    (void)Java_ee_ria_xroad_common_util_MemoryPasswordStoreProvider_read(
        &fake_env, NULL, (jstring)0x2, (jstring)0x3);

    if (double_free_detected) {
        fprintf(stderr, "FAIL: detected double-free of ret_array\n");
        return 1;
    }

    printf("OK: no double-free detected\n");
    return 0;
}
