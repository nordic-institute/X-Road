# Tests for the Green Metrics Tool

The `run-tests.sh` script runs tests in sequence and then in parallel. Most of the script values are hard-coded except
for the number or sequential and parallel tests. The script is designed to run on a Unix machine with `bash`, `curl`
and `sed` installed.

The intended use-case is running it inside a Docker container with the service client on `ss1`.

To override the default numbers (500 for both), run the script with the following arguments:

```bash
./run-tests.sh [sequential_test_count] [parallel_test_count]
```
