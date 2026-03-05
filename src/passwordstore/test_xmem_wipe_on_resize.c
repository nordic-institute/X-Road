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
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <sys/ipc.h>
#include <sys/shm.h>

#include "xmem.h"

static void fill_pattern(unsigned char *p, size_t n) {
    for (size_t i = 0; i < n; i++) p[i] = (unsigned char)0xA5;
}

static int all_zero(unsigned char *p, size_t n) {
    for (size_t i = 0; i < n; i++) {
        if (p[i] != 0) return 0;
    }
    return 1;
}

int main(void) {
    struct xmem xm;
    key_t sem_key = IPC_PRIVATE;
    key_t mem_key = IPC_PRIVATE;

    // Create
    if (xmem_open(&xm, sem_key, mem_key, 0600) != 0) {
        fprintf(stderr, "FAIL: xmem_open\n");
        return 1;
    }
    if (xmem_writelock(&xm) != 0) {
        fprintf(stderr, "FAIL: xmem_writelock\n");
        return 1;
    }

    // Create a segment of size 4096 and write pattern
    if (xmem_resize(&xm, 4096) != 0) {
        fprintf(stderr, "FAIL: xmem_resize initial\n");
        return 1;
    }
    unsigned char *p = (unsigned char*)xmem_ptr(&xm);
    if (!p) {
        fprintf(stderr, "FAIL: xmem_ptr\n");
        return 1;
    }
    fill_pattern(p, 4096);

    // Capture current dshmid from the pointer segment (same way xmem does)
    int old_dshmid = *((int *)xm.pshmptr);
    if (old_dshmid == -1) {
        fprintf(stderr, "FAIL: old_dshmid == -1\n");
        return 1;
    }

    // Trigger resize to a different size => old segment should be wiped before IPC_RMID
    if (xmem_resize(&xm, 1024) != 0) {
        fprintf(stderr, "FAIL: xmem_resize second\n");
        return 1;
    }

    // Attach to old segment id (it may still be attachable even after IPC_RMID until last detach)
    void *oldptr = shmat(old_dshmid, 0, 0);
    if (oldptr == (void*)-1) {
        // If it is already gone, that's acceptable; but then we can't assert wipe.
        // In practice, with your "wipe then IPC_RMID", it should still be attachable here.
        fprintf(stderr, "FAIL: could not shmat old segment to verify wipe\n");
        return 1;
    }

    int ok = all_zero((unsigned char*)oldptr, 4096);
    shmdt(oldptr);

    // Cleanup
    xmem_unlock(&xm);
    xmem_close(&xm);

    if (!ok) {
        fprintf(stderr, "FAIL: old shared memory not zeroed before removal\n");
        return 1;
    }

    printf("OK: old shared memory wiped before removal\n");
    return 0;
}
