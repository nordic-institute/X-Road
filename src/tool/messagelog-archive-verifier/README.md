## Messagelog archive verifier

This code verifies the correctness of the hash chain related to the log archive. It calculates the ASiC container hashes in the log archive (zip) file and compares them with the hashes in the linking info file.

If there are previous archived files, the script also takes into consideration the last hash step result of the previous archive file.

To use the script, make sure you have the matching Java version installed.

The script can be invoked using the following command:

```
java -jar messagelog-archive-verifier.jar <pathToArchiveFile.zip> <(lastHashStepResult) or (-f) or (--first)>
```

**NB!** If the value of the second argument is `-f` or `--first` (both case insensitive), it is assumed that the first archive file in the chain is being verified, and no previous hash steps have been calculated.

The standard output of the script is the result of the last hash step of the archive file.
