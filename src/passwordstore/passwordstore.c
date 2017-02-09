/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

#include <stdlib.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/types.h>

#include "xmem.h"

#define SHM_SEM_PROJ_ID 4000
#define SHM_MEM_PROJ_ID 4001

#include "passwordstore.h"


static int openSHMem(struct xmem *xm, int perms, int write_lock,
        const char *pathname_for_ftok)
{
    int xm_opened = 0;
    int t;
    key_t sem_key;
    key_t mem_key;
    int res = 0;

    sem_key = ftok(pathname_for_ftok, SHM_SEM_PROJ_ID);

    if (sem_key == -1) {
        res = CANNOT_DERIVE_KEY_FOR_LOCK;

        goto error;
    }

    mem_key = ftok(pathname_for_ftok, SHM_MEM_PROJ_ID);

    if (mem_key == -1) {
        res = CANNOT_DERIVE_KEY_FOR_MEM;

        goto error;
    }

    if (xmem_open(xm, sem_key, mem_key, perms) != 0) {
        res = CANNOT_OPEN_SHARED_MEMORY;

        goto error;
    }

    xm_opened = 1;

    if (write_lock) {
        t = xmem_writelock(xm);
    } else {
        t = xmem_readlock(xm);
    }

    if (t != 0) {
        res = CANNOT_LOCK_SHARED_MEMORY;

        goto error;
    }

    return res;

error:
    if (xm_opened) {
        xmem_detach(xm);
    }

    return res;
}


static int closeSHMem(struct xmem *xm)
{
    if (xmem_unlock(xm) != 0) {
        return CANNOT_UNLOCK_SHARED_MEMORY;
    }

    if (xmem_detach(xm) != 0) {
        return CANNOT_CLOSE_SHARED_MEMORY;
    }

    return 0;
}

// Represents the record in password database.
struct PasswordDbRecord {
    // Password ID
    char *id;
    // Length of password ID
    size_t id_len;

    // Password (can be NULL)
    char *password;
    // Length of password.
    size_t password_len;
};

// Reads database record from *db. Assumes that the *db has *db_len bytes
// left.
// Updates *db and *db_len so that they reflect the position after the read
// record.
static int readDbRecord(const char **db, size_t *db_len,
        struct PasswordDbRecord *rec)
{
    size_t data_len;

    rec->id_len = 0;
    rec->password_len = 0;

    if (*db_len == 0) {
        // At the EOF
        return 0;
    }

    // check if we have room for lengths
    if (*db_len < 2 * sizeof(size_t)) {
        return CORRUPTED_PASSWORD_STORE;
    }

    rec->id_len = *((size_t *) *db);
    rec->password_len = *(((size_t *) *db) + 1);
    // Calculate total length of data.
    data_len = 2 * sizeof(size_t) + rec->id_len + rec-> password_len;

    // Check if we have room for data items.
    if (*db_len < data_len) {
        return CORRUPTED_PASSWORD_STORE;
    }

    // OK, we can read the data.
    *db += 2 * sizeof(size_t);
    rec->id = (char *) *db;
    *db += rec->id_len;
    rec->password = (char *) *db;
    *db += rec->password_len;

    *db_len -= data_len;

    return 0;
}

// Writes password record to db, starting from offset *db_len.
// Also, updates *db_len so that it points to the offset after the end
// of the written record.
static void writeDbRecord(char *db, size_t *db_len,
        struct PasswordDbRecord *rec)
{
    if (rec->password == NULL || rec->password_len == 0) {
        // Don't write empty passwords.
        return;
    }

    // Write len of ID
    *((size_t *)(db + *db_len)) = rec->id_len;
    *db_len += sizeof(size_t);

    // Write len of password
    *((size_t *)(db + *db_len)) = rec->password_len;
    *db_len += sizeof(size_t);

    // Copy ID
    memcpy(db + *db_len, rec->id, rec->id_len);
    *db_len += rec->id_len;

    // Copy password
    memcpy(db + *db_len, rec->password, rec->password_len);
    *db_len += rec->password_len;
}

// Reencodes the password database, modifying the entry corresponding
// to new_id. If ID is not present in DB, it is added. If it is present,
// then the entry is modified. If new_pw is null, then the corresponding
// entry is removed. Returns new database in parameters encoded and encoded_len.
// The encoded pointer must be freed by the caller.

// The database records have the following format:
// * length of ID -- sizeof(size_t) bytes in host order.
// * length of password -- sizeof(size_t) bytes in host order. Can be 0.
// * password ID
// * password
static int reencodePasswordDb(const char *original, size_t original_len,
        char *new_id, char *new_pw, size_t new_pw_len,
        char **encoded, size_t *encoded_len)
{
    int res = 0;
    size_t new_id_len = strlen(new_id);
    struct PasswordDbRecord record;

    *encoded_len = 0;
    // Max size of new database: current database + length of added record.
    // This max size may not materialize if an entry is changed or deleted.
    // record is: 2xlength + id + pw
    *encoded = malloc(original_len +
            2 * sizeof(size_t) + strlen(new_id) + new_pw_len);

    if (*encoded == NULL) {
        res = MALLOC_FAILURE;
        goto error;
    }

    // Copy records from the old database to new one, removing the record
    // with new_id (if present).
    while ((res = readDbRecord(&original, &original_len, &record)) == 0) {
        if (record.id_len == 0 && record.password_len == 0) { // at EOF
            break;
        }

        // Check if it the copied password is the one added to database.
        // If so, don't copy it.
        if (record.id_len == new_id_len &&
                memcmp(record.id, new_id, new_id_len) == 0) {
            continue;
        }

        // Copy the record to new database.
        writeDbRecord(*encoded, encoded_len, &record);
    }
    if (res != 0) {
        goto error;
    }

    // If the new password is not null, write it to the new database.
    if (new_pw_len != 0) {
        record.id = new_id;
        record.id_len = strlen(new_id);
        record.password = new_pw;
        record.password_len = new_pw_len;
        writeDbRecord(*encoded, encoded_len, &record);
    }

    // OK, we are done.

    return 0;
error:
    free(*encoded);
    *encoded = NULL;

    return res;
}

/* Main API functions. */

int LEGACY_passwordRead(const char *pathname_for_ftok, const char *password_id,
        char **ret_buf, int *ret_buf_len)
{
    const char *db = NULL;
    size_t db_len = 0;
    int res;
    struct xmem xm;
    int xm_opened = 0;
    struct PasswordDbRecord record;
    int password_id_len = strlen(password_id);

    *ret_buf = NULL;
    *ret_buf_len = 0;

    // Open and lock SHM.
    if ((res = openSHMem(&xm, 0, 0, pathname_for_ftok)) != 0) {
        goto error;
    }

    xm_opened = 1;

    db = xmem_ptr(&xm);
    db_len = xmem_len(&xm);

    // Iterate over records in SHM.
    while ((res = readDbRecord(&db, &db_len, &record)) == 0) {
        if (record.id_len == 0 && record.password_len == 0) { // at EOF
            // Not found. The ret_buf will be NULL.
            break;
        }

        // Check if it is our record.
        if (record.id_len == (size_t) password_id_len &&
                memcmp(record.id, password_id, password_id_len) == 0) {
            // Found. Fill in the ret_buf.
            *ret_buf = malloc(record.password_len);
            if (*ret_buf == NULL) {
                res = MALLOC_FAILURE;
                goto error;
            }
            memcpy(*ret_buf, record.password, record.password_len);
            *ret_buf_len = record.password_len;
            break;
        }
    }

    if (res != 0) {
        goto error;
    }

    xm_opened = 0;

    // Close and unlock SHM.
    if ((res = closeSHMem(&xm)) != 0) {
        goto error;
    }

    return 0;
error:

    if (xm_opened) {
        closeSHMem(&xm);
    }

    free(*ret_buf);
    *ret_buf = NULL;

    return res;
}

int LEGACY_passwordWrite(const char *pathname_for_ftok, const char *password_id,
        const char *password, int password_length, int permissions)
{
    struct xmem xm;
    int xm_opened = 0;
    int error_code = 0;

    // Password database that also contains the added password
    char *encoded_db = NULL;
    size_t encoded_db_len;

    char *xm_ptr;

    // Open shared memory and lock it.
    if ((error_code = openSHMem(&xm, permissions, 1, pathname_for_ftok)) != 0) {
        goto error;
    }

    xm_opened = 1;

    // Rewrite the contents of the shared memory, adding/removing/replacing
    // the given password.

    // We have to uncast the const because the reencode does not want
    // to define parameters as const.
    if ((error_code = reencodePasswordDb(xmem_ptr(&xm), xmem_len(&xm),
            (char *) password_id, (char *) password, password_length,
            &encoded_db, &encoded_db_len)) != 0) {
        goto error;
    }

    // Write the re-encoded database back to SHM.
    if (xmem_resize(&xm, encoded_db_len) != 0) {
        error_code = CANNOT_RESIZE_SHARED_MEMORY;

        goto error;
    }

    if (encoded_db_len != 0) { // Only copy stuff if db is not empty.
        xm_ptr = xmem_ptr(&xm);

        if (xm_ptr == NULL) {
            error_code = CANNOT_GET_SHARED_MEMORY_POINTER;

            goto error;
        }

        memcpy(xm_ptr, encoded_db, encoded_db_len);
    }

    xm_opened = 0;

    // Release and unlock SHM
    if ((error_code = closeSHMem(&xm)) != 0) {
        goto error;
    }
    free(encoded_db);

    return error_code;

error:
    free(encoded_db);
    if (xm_opened) {
        closeSHMem(&xm);
    }

    return error_code;
}

int LEGACY_passwordClear(const char *pathname_for_ftok, int permissions)
{
    struct xmem xm;
    int xm_opened = 0;
    int error_code = 0;

    // Open shared memory and lock it.
    if ((error_code = openSHMem(&xm, permissions, 1, pathname_for_ftok)) != 0) {
        goto error;
    }

    xm_opened = 1;

    // Write the re-encoded database back to SHM.
    if (xmem_resize(&xm, 0) != 0) {
        error_code = CANNOT_RESIZE_SHARED_MEMORY;

        goto error;
    }

    xm_opened = 0;

    // Release and unlock SHM
    if ((error_code = closeSHMem(&xm)) != 0) {
        goto error;
    }

    return error_code;

error:
    if (xm_opened) {
        closeSHMem(&xm);
    }

    return error_code;
}


const char* LEGACY_strError(int error_code)
{
    switch (error_code) {
        case CANNOT_CLOSE_SHARED_MEMORY:
            return "Cannot close shared memory";
        case CANNOT_DERIVE_KEY_FOR_LOCK:
            return "Cannot derive key for lock";
        case CANNOT_DERIVE_KEY_FOR_MEM:
            return "Cannot derive key for mem";
        case CANNOT_GET_SHARED_MEMORY_POINTER:
            return "Cannot get shared memory pointer";
        case CANNOT_LOCK_SHARED_MEMORY:
            return "Cannot lock shared memory";
        case CANNOT_OPEN_SHARED_MEMORY:
            return "Cannot open shared memory";
        case CANNOT_RESIZE_SHARED_MEMORY:
            return "Cannot resize shared memory";
        case CANNOT_UNLOCK_SHARED_MEMORY:
            return "Cannot unlock shared memory";
        case MALLOC_FAILURE:
            return "Malloc failure";
        case CORRUPTED_PASSWORD_STORE:
            return "Password store is corrupted";
    }

    return "UNKNOWN";
}
