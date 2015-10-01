#!/bin/bash

function do_read {
    ./test_xlock read 3; sleep 1; ./test_xlock read 3 
}

function do_write {
    sleep 1; ./test_xlock write 5; sleep 1; ./test_xlock write 5 
}

# panen käima testide komplektid.

do_read & 
do_read &
do_read &
do_write & 

wait

cat <<EOF
Actual output should look like this. NB! Redirection to file gives
wrong output.

Acquiring read lock.
Got lock, sleeping for 3 seconds.
Acquiring read lock.
Got lock, sleeping for 3 seconds.
Acquiring read lock.
Got lock, sleeping for 3 seconds.
Acquiring write lock.
Unlocking semaphore.
Unlocking semaphore.
Unlocking semaphore.
Got lock, sleeping for 5 seconds.
Acquiring read lock.
Acquiring read lock.
Acquiring read lock.
Unlocking semaphore.
Got lock, sleeping for 3 seconds.
Got lock, sleeping for 3 seconds.
Got lock, sleeping for 3 seconds.
Acquiring write lock.
Unlocking semaphore.
Unlocking semaphore.
Unlocking semaphore.
Got lock, sleeping for 5 seconds.
Unlocking semaphore.
EOF
