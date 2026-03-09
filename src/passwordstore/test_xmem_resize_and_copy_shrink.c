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
#include <stdlib.h>
#include <sys/ipc.h>

#include "xmem.h"

static void fill_pattern(unsigned char *p, size_t n) {
    size_t i;
    for (i = 0; i < n; i++) {
        p[i] = (unsigned char)(i % 251);
    }
}

static int check_pattern(unsigned char *p, size_t n) {
    size_t i;
    for (i = 0; i < n; i++) {
        if (p[i] != (unsigned char)(i % 251)) {
            return 0;
        }
    }
    return 1;
}

int main(void) {
    struct xmem xm;
    key_t sem_key = IPC_PRIVATE;
    key_t mem_key = IPC_PRIVATE;
    unsigned char *p = NULL;
    int opened = 0;
    int locked = 0;
    int rc = 1;
    size_t old_size = 4096;
    size_t new_size = 1024;

    if (xmem_open(&xm, sem_key, mem_key, 0600) != 0) {
        fprintf(stderr, "FAIL: xmem_open\n");
        goto cleanup;
    }
    opened = 1;

    if (xmem_writelock(&xm) != 0) {
        fprintf(stderr, "FAIL: xmem_writelock\n");
        goto cleanup;
    }
    locked = 1;

    if (xmem_resize(&xm, old_size) != 0) {
        fprintf(stderr, "FAIL: xmem_resize old_size\n");
        goto cleanup;
    }

    p = (unsigned char *)xmem_ptr(&xm);
    if (p == NULL) {
        fprintf(stderr, "FAIL: xmem_ptr old\n");
        goto cleanup;
    }

    fill_pattern(p, old_size);

    if (xmem_resize_and_copy(&xm, new_size) != 0) {
        fprintf(stderr, "FAIL: xmem_resize_and_copy shrink\n");
        goto cleanup;
    }

    if (xmem_len(&xm) != new_size) {
        fprintf(stderr, "FAIL: xmem_len mismatch\n");
        goto cleanup;
    }

    p = (unsigned char *)xmem_ptr(&xm);
    if (p == NULL) {
        fprintf(stderr, "FAIL: xmem_ptr new\n");
        goto cleanup;
    }

    if (!check_pattern(p, new_size)) {
        fprintf(stderr, "FAIL: copied prefix mismatch after shrink\n");
        goto cleanup;
    }

    printf("OK: xmem_resize_and_copy safely shrinks and preserves prefix\n");
    rc = 0;

cleanup:
    if (locked) {
        xmem_unlock(&xm);
    }
    if (opened) {
        xmem_close(&xm);
    }
    return rc;
}
