#!/bin/bash

data_path=${1:-./}
sequential=${2:-500}
parallel=${3:-500}

# Test tasks
getRandom() {
  # Change the request id in the request so we don't alawys send the same input
  sed "s/_REQUEST_ID_/get_random_$1/" "$data_path/getRandom.xml" | curl -s -d @- --header "Content-Type: text/xml" -X POST http://ss1:8080 > /dev/null
}

helloService() {
  # Change the request id and name in the request so we don't alawys send the same input
  sed "s/_REQUEST_ID_/get_hello_$1/" "$data_path/helloService.xml" | sed "s/_NAME_/Test$1/" | curl -s -d @- --header "Content-Type: text/xml" -X POST http://ss1:8080 > /dev/null
}

rest_1() {
  curl -s -d "@$data_path/rest-test-1.json" --header "Content-Type: application/json" --header "X-Road-Client: DEV/COM/4321/TestClient" -X POST http://ss1:8080/r1/DEV/COM/1234/TestService/mock1 > /dev/null
}

# Sequential tests
echo "Running sequential tests ..."
echo "Running $sequential getRandom ..."
for i in $(seq 1 $sequential); do
  getRandom $i
done
echo "Running $sequential helloService ..."
for i in $(seq 1 $sequential); do
  helloService $i
done
echo "Running $sequential rest_1 ..."
for i in $(seq 1 $sequential); do
  rest_1
done
echo "Sequential tests done."

# Parallel tests
echo "Running $parallel parallel tests ..."
BATCH_SIZE=5
(
  for i in $(seq 1 $parallel); do
    ((i % BATCH_SIZE == 0)) && wait
    getRandom $i &
    helloService $i &
    rest_1 &
  done
  wait
)
echo "Parallel tests done."
