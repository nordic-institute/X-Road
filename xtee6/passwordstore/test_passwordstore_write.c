#include <stdio.h>
#include <string.h>

#include "passwordstore.h"

int main(int argc, char **argv)
{
    int err;

    if (argc != 3) {
        fprintf(stderr, "Usage: %s <id> <pwd>\n", argv[0]);
        return(1);
    }

    err = LEGACY_passwordWrite("/", argv[1], argv[2], strlen(argv[2]), 0600);
    if (err != 0) {
        fprintf(stderr, "ERROR: %s\n", LEGACY_strError(err));
        return 1;
    }

    return 0;
}
