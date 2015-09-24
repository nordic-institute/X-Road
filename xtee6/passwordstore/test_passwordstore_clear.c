#include <stdio.h>
#include <string.h>

#include "passwordstore.h"

int main(int argc, char **argv)
{
    int err;
    (void) argc;
    (void) argv;

    err = LEGACY_passwordClear("/", 0600);
    if (err != 0) {
        fprintf(stderr, "ERROR: %s\n", LEGACY_strError(err));
        return 1;
    }

    return 0;
}
