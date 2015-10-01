#include <grp.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "xlock.h"

int main(int argc, char **argv)
{
    XLock *sem = NULL;
    int sec_count;
    int res;

    if (argc != 3) {
        printf("Usage: %s {read|write} <number of seconds>\n", argv[0]);
        goto error;
    }

    sec_count = atoi(argv[2]);

    if ((res = LCK_new("/tmp", getgid(), &sem)) != 0) {
        printf("Cannot open semaphore: %s", LCK_strError(res));
        goto error;
    }

    if (strcmp(argv[1], "read") == 0) {
        printf("Acquiring read lock.\n");
        res = LCK_readLock(sem);

    } else if (strcmp(argv[1], "write") == 0) {
        printf("Acquiring write lock.\n");
        res = LCK_writeLock(sem);

    } else {
        printf("Usage: %s {read|write} <number of seconds>\n", argv[0]);
        goto error;
    }

    if (res != 0) {
        printf("Cannot obtain lock: %s", LCK_strError(res));
        goto error;
    }

    printf("Got lock, sleeping for %d seconds.\n", sec_count);
    sleep(sec_count);

    printf("Unlocking semaphore.\n");
    if ((res = LCK_unlock(sem)) != 0) {
        printf("Cannot unlock semaphore: %s", LCK_strError(res));
        goto error;
    }

    LCK_free(sem);
    return 0;

error:
    LCK_free(sem);
    return 1;
}
