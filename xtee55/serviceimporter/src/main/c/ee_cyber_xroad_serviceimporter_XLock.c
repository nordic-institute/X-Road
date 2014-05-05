#include "ee_cyber_xroad_serviceimporter_XLock.h"

#include <errno.h>
#include <grp.h>
#include <stdio.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/types.h>
#include <unistd.h>

#include "xlock.h"

#define XROAD_GROUP "xtee"

JNIEXPORT void JNICALL
Java_ee_cyber_xroad_serviceimporter_XLock_init(JNIEnv *env, jobject this,
    jstring j_pathname_for_ftok)
{
    XLock *lock;
    gid_t lock_gid;

    int error_code;
    const char *pathname_for_ftok = NULL;

    char buf[1024];
    struct group grp;
    struct group *grp_ptr = NULL;

    memset(&buf, 0, sizeof(buf));
    if (getgrnam_r(XROAD_GROUP, &grp, buf, sizeof(buf), &grp_ptr) == 0
            && grp_ptr != NULL) {
        lock_gid = grp.gr_gid;
    } else {
        lock_gid = getgid();
    }

    if (j_pathname_for_ftok != NULL) {
        pathname_for_ftok =
            (*env)->GetStringUTFChars(env, j_pathname_for_ftok, NULL);
    }

    if ((error_code = LCK_new(pathname_for_ftok, lock_gid, &lock)) != 0) {
        goto error;
    }

    jclass this_class = (*env)->GetObjectClass(env, this);

    jfieldID fid_sem_id = (*env)->GetFieldID(env, this_class, "semId", "I");
    jfieldID fid_rl_count = (*env)->GetFieldID(env, this_class, "rlCount", "I");
    jfieldID fid_wl_count = (*env)->GetFieldID(env, this_class, "wlCount", "I");

    (*env)->SetIntField(env, this, fid_sem_id, lock->sem_id);
    (*env)->SetIntField(env, this, fid_rl_count, lock->rl_count);
    (*env)->SetIntField(env, this, fid_wl_count, lock->wl_count);

error: ;
    int errno_saved = errno;

    if (j_pathname_for_ftok != NULL) {
        (*env)->ReleaseStringUTFChars(
            env, j_pathname_for_ftok, pathname_for_ftok);
    }

    LCK_free(lock);

    if (error_code != 0) {
        const char *error_str = LCK_strError(error_code);

        errno = errno_saved;
        perror(error_str);

        (*env)->ThrowNew(env,
            (*env)->FindClass(env, "java/lang/RuntimeException"), error_str);
    }
}

JNIEXPORT void JNICALL
Java_ee_cyber_xroad_serviceimporter_XLock_readLock(JNIEnv *env, jobject this)
{
    XLock lock;
    int error_code;

    jclass this_class = (*env)->GetObjectClass(env, this);

    jfieldID fid_sem_id = (*env)->GetFieldID(env, this_class, "semId", "I");
    jfieldID fid_rl_count = (*env)->GetFieldID(env, this_class, "rlCount", "I");
    jfieldID fid_wl_count = (*env)->GetFieldID(env, this_class, "wlCount", "I");

    lock.sem_id = (*env)->GetIntField(env, this, fid_sem_id);
    lock.rl_count = (*env)->GetIntField(env, this, fid_rl_count);
    lock.wl_count = (*env)->GetIntField(env, this, fid_wl_count);

    if ((error_code = LCK_readLock(&lock)) != 0) {
        goto error;
    }

    (*env)->SetIntField(env, this, fid_sem_id, lock.sem_id);
    (*env)->SetIntField(env, this, fid_rl_count, lock.rl_count);
    (*env)->SetIntField(env, this, fid_wl_count, lock.wl_count);

error:
    if (error_code != 0) {
        (*env)->ThrowNew(env,
                (*env)->FindClass(env, "java/lang/RuntimeException"),
                LCK_strError(error_code));
    }
}

JNIEXPORT void JNICALL
Java_ee_cyber_xroad_serviceimporter_XLock_writeLock(JNIEnv *env, jobject this)
{
    XLock lock;
    int error_code;

    jclass this_class = (*env)->GetObjectClass(env, this);

    jfieldID fid_sem_id = (*env)->GetFieldID(env, this_class, "semId", "I");
    jfieldID fid_rl_count = (*env)->GetFieldID(env, this_class, "rlCount", "I");
    jfieldID fid_wl_count = (*env)->GetFieldID(env, this_class, "wlCount", "I");

    lock.sem_id = (*env)->GetIntField(env, this, fid_sem_id);
    lock.rl_count = (*env)->GetIntField(env, this, fid_rl_count);
    lock.wl_count = (*env)->GetIntField(env, this, fid_wl_count);

    if ((error_code = LCK_writeLock(&lock)) != 0) {
        goto error;
    }

    (*env)->SetIntField(env, this, fid_sem_id, lock.sem_id);
    (*env)->SetIntField(env, this, fid_rl_count, lock.rl_count);
    (*env)->SetIntField(env, this, fid_wl_count, lock.wl_count);

error:
    if (error_code != 0) {
        (*env)->ThrowNew(env,
                (*env)->FindClass(env, "java/lang/RuntimeException"),
                LCK_strError(error_code));
    }
}

JNIEXPORT void JNICALL
Java_ee_cyber_xroad_serviceimporter_XLock_unlock(JNIEnv *env, jobject this)
{
    XLock lock;
    int error_code;

    jclass this_class = (*env)->GetObjectClass(env, this);

    jfieldID fid_sem_id = (*env)->GetFieldID(env, this_class, "semId", "I");
    jfieldID fid_rl_count = (*env)->GetFieldID(env, this_class, "rlCount", "I");
    jfieldID fid_wl_count = (*env)->GetFieldID(env, this_class, "wlCount", "I");

    lock.sem_id = (*env)->GetIntField(env, this, fid_sem_id);
    lock.rl_count = (*env)->GetIntField(env, this, fid_rl_count);
    lock.wl_count = (*env)->GetIntField(env, this, fid_wl_count);

    if ((error_code = LCK_unlock(&lock)) != 0) {
        goto error;
    }

    (*env)->SetIntField(env, this, fid_sem_id, lock.sem_id);
    (*env)->SetIntField(env, this, fid_rl_count, lock.rl_count);
    (*env)->SetIntField(env, this, fid_wl_count, lock.wl_count);

error:
    if (error_code != 0) {
        (*env)->ThrowNew(env,
                (*env)->FindClass(env, "java/lang/RuntimeException"),
                LCK_strError(error_code));
    }
}
