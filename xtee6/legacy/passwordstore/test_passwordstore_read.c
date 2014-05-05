#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "passwordstore.h"

int main(int argc, char **argv)
{
    int err;
    char *ret = NULL;
    int ret_len = 0;

    if (argc != 2) {
        fprintf(stderr, "Usage: %s <id>\n", argv[0]);
        return(1);
    }

    err = LEGACY_passwordRead("/", argv[1], &ret, &ret_len);
    if (err != 0) {
        fprintf(stderr, "ERROR: %s\n", LEGACY_strError(err));
        return 1;
    }

    printf("Password: \"");
    fwrite(ret, 1, ret_len, stdout);
    printf("\"\n");

    free(ret);

    return 0;
}
