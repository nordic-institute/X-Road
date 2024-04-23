# Tests for the Green Metrics Tool

The `run-tests.sh` script runs tests in sequence and then in parallel. Most of the script values are hard-coded except
for the number or sequential and parallel tests. The script is designed to run on a Unix machine with `bash`, `curl`
and `sed` installed.

The intended use-case is running it inside a Docker container with the service client on `ss1`.

Arguments are as follows:

* 1 - Path to requests, defaults to .
* 2 - Number of sequential tests, defaults to 500
* 3 - Number of parallel tests, defaults to 500

To override the default numbers (500 for both), run the script with the following arguments:

```bash
./run-tests.sh [path_to_requests] [sequential_test_count] [parallel_test_count]
```
