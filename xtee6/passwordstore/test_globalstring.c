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
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "globalstring.h"


const char *s1 = "First global string";
const char *s2 = "Second, longer, global string";
const char *s3 = "Third global string";

static const char *pathname_for_ftok = "./test_globalstring.c";

static int checkString(const char *str)
{
    char *read = NULL;
    int rc;

    if ((rc = LEGACY_globalStringWrite(
                    pathname_for_ftok, str, 0640)) != 0) {
        printf("Cannot write global string: %s\n",
                LEGACY_strError(rc));

        goto error;
    }

    read = LEGACY_globalStringRead(pathname_for_ftok, &rc);
    
    if (read == NULL) {
        if (rc != 0) {
            printf("Cannot read global string: %s\n",
                    LEGACY_strError(rc));
        } else {
            printf("Global string was empty!");
        }

        goto error;
    }

    if (strcmp(read, str) != 0) {
        printf("Wrote \"%s\", read \"%s\".\n", str, read);

        goto error;
    }
    
    free(read);

    return 1;
    
error:
    free(read);
    
    return 0;
}


int main()
{
    int rc;

    if (!checkString(s1)) {
        printf("Error while processing \"%s\"\n", s1);
        
        goto error;
    }

    if ((rc = LEGACY_globalStringWrite(pathname_for_ftok, NULL, 0640)) != 0) {
        printf("Error writing NULL to SHM: %s\n",
                LEGACY_strError(rc));
        
        goto error;
    }

    if (LEGACY_globalStringRead(pathname_for_ftok, &rc) != NULL) {
        printf("Global string is not NULL\n");
        
        goto error;
    } else {
        if (rc != 0) {
            printf("Cannot read global string: %s\n",
                    LEGACY_strError(rc));
        }
    }

    if (!checkString(s2)) {
        printf("Error while processing \"%s\"\n", s2);
        goto error;
    }
    
    if (!checkString(s3)) {
        printf("Error while processing \"%s\"\n", s3);
        goto error;
    }

    printf("Everything was beautiful, and nobody got hurt.\n");

    return 0;

error:
    return 1;
}
