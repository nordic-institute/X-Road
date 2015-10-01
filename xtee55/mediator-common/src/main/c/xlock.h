#ifndef XLCK_H_INCLUDED

#include <sys/types.h>

#ifdef  __cplusplus
extern "C" {
#endif

typedef enum {
    MALLOC_FAILURE = 1,
    READ_LOCK_ALREADY_EXISTS,
    WRITE_LOCK_ALREADY_EXISTS,
    CANNOT_DERIVE_KEY_FOR_LOCK,
    CANNOT_GET_READ_LOCK,
    CANNOT_GET_WRITE_LOCK,
    CANNOT_GET_SEMAPHORE_INFO,
    CANNOT_SET_SEMAPHORE_PERMISSIONS,
    CANNOT_SET_SEMAPHORE_VALUE,
    CANNOT_REMOVE_LOCK,
    LOCK_NOT_ACQUIRED,
    SEMOP_FAILED,
    SEMGET_FAILED
} XLOCK_ERRORS;

/** project id for ftok */
#define LCK_XCONF_PROJ_ID 42

/**
 * XLock is a read-write lock, with one writer or multiple readers at
 * once. Based on SysV semaphore API.
 */
typedef struct _xlock_impl {
    int sem_id;
    int rl_count;
    int wl_count;
} XLock;

/**
 * Initializes current XLock or creates it if it does not exist
 * already.
 * \return Returns 0 on success.
 */
int LCK_new(const char *pathname_for_ftok, gid_t gid, XLock **xlock);

/**
 * Frees the memory allocated for XLock by LCK_new.
 */
void LCK_free(XLock *xlock);

/**
 * Acquires a read lock. Blocks until success or a signal.
 * \return Returns 0 on success.
 */
int LCK_readLock(XLock *xlock);

/**
 * Acquires a write lock. Blocks until success or a signal.
 * \return Returns 0 on success.
 */
int LCK_writeLock(XLock *xlock);

/**
 * Releases previously acquired lock.
 * \return Returns 0 on success.
 */
int LCK_unlock(XLock *xlock);

const char* LCK_strError(int error_code);

#ifdef  __cplusplus
}
#endif

#endif /* #ifndef XLCK_H_INCLUDED */
