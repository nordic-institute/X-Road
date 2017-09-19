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
#ifndef PASSWORD_STORE_H
#define PASSWORD_STORE_H

#ifdef  __cplusplus
extern "C" {
#endif

/** List of supported error codes. */
typedef enum {
    CANNOT_CLOSE_SHARED_MEMORY = 1,
    CANNOT_DERIVE_KEY_FOR_LOCK,
    CANNOT_DERIVE_KEY_FOR_MEM,
    CANNOT_GET_SHARED_MEMORY_POINTER,
    CANNOT_LOCK_SHARED_MEMORY,
    CANNOT_OPEN_SHARED_MEMORY,
    CANNOT_RESIZE_SHARED_MEMORY,
    CANNOT_UNLOCK_SHARED_MEMORY,
    MALLOC_FAILURE,
    CORRUPTED_PASSWORD_STORE
} PASSWORD_STORE_ERRORS;



/**
 * Reads string from shared memory.
 *
 * \param pathname_for_ftok pathname for ftok() function.
 * \param password_id Identifier for the password to retrieve.
 * \param ret_buf This will be modified to contain reference to the result.
 * The caller must free the result using free() function.
 * \param ret_buf_len This will contain number of bytes in the ret_buf.
 * \return 0 on success, otherwise error code.
 */
int LEGACY_passwordRead(const char *pathname_for_ftok, const char *password_id,
        char **ret_buf, int *ret_buf_len);

/**
 * Writes string to shared memory.
 *
 * \param pathname_for_ftok pathname for ftok() function.
 * \param password_id Identifier for the password to be written.
 * \param password Password to write. If this is null, password with given
 * identifier is removed from shared memory.
 * \param password_length Length of the password field.
 * \param permissions Permissions for shared memory segment if segment is not
 * created yet.
 *
 * \return Returns 0 on success, otherwise error code.
 */
int LEGACY_passwordWrite(const char *pathname_for_ftok, const char *password_id,
        const char *password, int password_length, int permissions);

/**
 * Clears the shared memory segment.
 *
 * \param pathname_for_ftok pathname for ftok() function.
 * \param permissions Permissions for shared memory segment if segment is not
 * created yet.
 *
 * \return Returns 0 on success, otherwise error code.
 */
int LEGACY_passwordClear(const char *pathname_for_ftok, int permissions);

/**
 * Returns string describing error code. The caller must not free this pointer.
 */
const char* LEGACY_strError(int error_code);


#ifdef  __cplusplus
}
#endif

#endif
