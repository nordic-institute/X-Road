#include <assert.h>
#include <errno.h>
#include <grp.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/sem.h>
#include <sys/shm.h>
#include <sys/types.h>
#include <syslog.h>
#include <unistd.h>

#include "xlock.h"

#if defined(__GNU_LIBRARY__) && !defined(_SEM_SEMUN_UNDEFINED)
/* union semun is defined by including <sys/sem.h> */
#else
/* according to X/OPEN we have to define it ourselves */
union semun {
    int val;                   /* value for SETVAL */
    struct semid_ds *buf;      /* buffer for IPC_STAT, IPC_SET */
    unsigned short int *array; /* array for GETALL, SETALL */
    struct seminfo *__buf;     /* buffer for IPC_INFO */
};
#endif

/*
 * Initial semaphore value. Readlock acquires one lock, writelock
 * acquires all of them.
 */
#define MAXLOCKS 10000

int LCK_new(const char *pathname_for_ftok, gid_t gid, XLock **xlock)
{
    assert(xlock != NULL);

    int ret = 0;
    int errno_saved = 0;
    key_t sem_key;

    *xlock = (XLock *) malloc(sizeof(XLock));
    if (*xlock == NULL) {
        ret = MALLOC_FAILURE;
        goto error;
    }

    (*xlock)->sem_id = -1;
    (*xlock)->rl_count = 0;
    (*xlock)->wl_count = 0;

    if ((sem_key = ftok(pathname_for_ftok, LCK_XCONF_PROJ_ID)) == -1) {
        ret = CANNOT_DERIVE_KEY_FOR_LOCK;
        goto error;
    }

    /* Creating semaphore. */
    (*xlock)->sem_id = semget(sem_key, 1, IPC_CREAT | IPC_EXCL |
        SHM_R | SHM_W | SHM_R >> 3 | SHM_W >> 3);

    if ((*xlock)->sem_id >= 0) {
        /* Semaphore created, initializing it. */
        union semun arg;
        struct sembuf ops[2];
        struct semid_ds seminfo;

        /* Make semaphore accessible for the group, to avoid race on startup. */
        memset(&seminfo, 0, sizeof(seminfo));
        if (semctl((*xlock)->sem_id, 0, IPC_STAT, &seminfo) < 0) {
            ret = CANNOT_GET_SEMAPHORE_INFO;
            goto error;
        }

        seminfo.sem_perm.gid = gid;
        if (semctl((*xlock)->sem_id, 0, IPC_SET, &seminfo) < 0) {
            ret = CANNOT_SET_SEMAPHORE_PERMISSIONS;
            goto error;
        }

        arg.val = MAXLOCKS;
        if (semctl((*xlock)->sem_id, 0, SETVAL, arg) < 0) {
            /* Who is going to initialize it now? */
            ret = CANNOT_SET_SEMAPHORE_VALUE;
            goto error;
        }

        /* Perform a semop for other processes to be able to use it. */
        ops[0].sem_num = 0;
        ops[0].sem_op = -MAXLOCKS;
        ops[0].sem_flg = SEM_UNDO;
        ops[1].sem_num = 0;
        ops[1].sem_op = MAXLOCKS;
        ops[1].sem_flg = SEM_UNDO;
        if (semop((*xlock)->sem_id, ops, 2) != 0) {
            ret = SEMOP_FAILED;
            goto error;
        }

    } else {
        /* Semaphore exists, is it initialized? */
        union semun arg;
        struct semid_ds seminfo;

        (*xlock)->sem_id = semget(sem_key, 0, 0);
        if ((*xlock)->sem_id < 0) {
            ret = SEMGET_FAILED;
            goto error;
        }

        /* Wait until semaphore is initialized. */
        arg.buf = &seminfo;
        while (1) {
            if (semctl((*xlock)->sem_id, 0, IPC_STAT, arg) < 0) {
                ret = CANNOT_GET_SEMAPHORE_INFO;
                goto error;
            }

            if (arg.buf->sem_otime != 0) {
                break;
            } else {
                sleep(1);
            }
        }
    }

    assert((*xlock)->sem_id >= 0);

    return 0;

error:
    errno_saved = errno;

    free(*xlock);
    *xlock = NULL;

    errno = errno_saved;

    return ret;
}

void LCK_free(XLock *xlock)
{
    free(xlock);
}

int LCK_readLock(XLock *xlock)
{
    struct sembuf ops[1];

    assert(xlock != NULL);

    if (xlock->wl_count > 0) {
        return WRITE_LOCK_ALREADY_EXISTS;
    }

    if (xlock->rl_count > 0) {
        xlock->rl_count++;
        return 0;
    }

    ops[0].sem_num = 0;
    ops[0].sem_op = -1;
    ops[0].sem_flg = SEM_UNDO;
    if (semop(xlock->sem_id, ops, 1) != 0) {
        return CANNOT_GET_READ_LOCK;
    }

    xlock->rl_count++;

    return 0;
}

int LCK_writeLock(XLock *xlock)
{
    struct sembuf ops[1];

    assert(xlock != NULL);

    if (xlock->rl_count > 0) {
        return READ_LOCK_ALREADY_EXISTS;
    }

    if (xlock->wl_count > 0) {
        xlock->wl_count++;
        return 0;
    }

    ops[0].sem_num = 0;
    ops[0].sem_op = -MAXLOCKS;
    ops[0].sem_flg = SEM_UNDO;
    if (semop(xlock->sem_id, ops, 1) != 0) {
        return CANNOT_GET_WRITE_LOCK;
    }

    xlock->wl_count++;

    return 0;
}

int LCK_unlock(XLock *xlock)
{
    struct sembuf ops[1];

    if (xlock->rl_count > 1) {
        xlock->rl_count--;
        return 0;
    }
    if (xlock->wl_count > 1) {
        xlock->wl_count--;
        return 0;
    }

    ops[0].sem_num = 0;
    ops[0].sem_op = 1 * xlock->rl_count + MAXLOCKS * xlock->wl_count;
    ops[0].sem_flg = SEM_UNDO;

    if (semop(xlock->sem_id, ops, 1) != 0) {
        return CANNOT_REMOVE_LOCK;
    }

    if (xlock->rl_count == 1) {
        xlock->rl_count--;
    } else {
        if (xlock->wl_count == 1) {
            xlock->wl_count--;
        } else {
            return LOCK_NOT_ACQUIRED;
        }
    }

    return 0;
}

int LCK_unlockAll(XLock *xlock)
{
    assert(xlock != NULL);

    int res;

    while (xlock->rl_count > 0) {
        if ((res = LCK_unlock(xlock)) != 0) {
            return res;
        }
    }

    while (xlock->wl_count > 0) {
        if ((res = LCK_unlock(xlock)) != 0) {
            return res;
        }
    }

    return 0;
}

const char* LCK_strError(int error_code)
{
    switch (error_code) {
    case MALLOC_FAILURE:
        return "Malloc failure";
    case READ_LOCK_ALREADY_EXISTS:
        return "Read lock already exists";
    case WRITE_LOCK_ALREADY_EXISTS:
        return "Write lock already exists";
    case CANNOT_DERIVE_KEY_FOR_LOCK:
        return "Cannot derive key for lock";
    case CANNOT_GET_READ_LOCK:
        return "Cannot get read lock";
    case CANNOT_GET_WRITE_LOCK:
        return "Cannot get write lock";
    case CANNOT_GET_SEMAPHORE_INFO:
        return "Cannot get semaphore";
    case CANNOT_SET_SEMAPHORE_PERMISSIONS:
        return "Cannot set semaphore permissions";
    case CANNOT_SET_SEMAPHORE_VALUE:
        return "Cannot set semaphore value";
    case CANNOT_REMOVE_LOCK:
        return "Cannot remove lock";
    case LOCK_NOT_ACQUIRED:
        return "Lock not acquired";
    case SEMOP_FAILED:
        return "semop() failed";
    case SEMGET_FAILED:
        return "semget() failed";
    }

    return "UNKNOWN";
}
