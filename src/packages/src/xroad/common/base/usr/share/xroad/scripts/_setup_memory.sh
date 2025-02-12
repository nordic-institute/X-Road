#!/bin/bash

die () {
  echo >&2 "$@"
  exit 1
}

size_rx="([0-9]+)([kKmMgG]?)"
initial_heap_size_rx="(-Xms|-XX:InitialHeapSize=)($size_rx)"
max_heap_size_rx="(-Xmx|-XX:MaxHeapSize=)($size_rx)"

function to_bytes() {
  local -r regex="^${size_rx}$"
  if [[ $1 =~ $regex ]]; then
    local -r value=${BASH_REMATCH[1]}
    local -r unit=${BASH_REMATCH[2]}

    case $unit in
      k|K)
        echo "$value * 1024" | bc
        ;;
      m|M)
        echo "$value * 1024 * 1024" | bc
        ;;
      g|G)
        echo "$value * 1024 * 1024 * 1024" | bc
        ;;
      *)
        echo $value
        ;;
    esac
  else
    echo ""
  fi
}

get_params_line() {
  grep --color=never "^${1}=" "/etc/xroad/services/local.properties"
}

apply_memory_config(){
  local -r params_file="/etc/xroad/services/local.properties"
  local -r params="$(get_params_line "$1")"

  local -r xms="-Xms$2"
  local -r xmx="-Xmx$3"

  if [ -z "$params" ]; then
    echo "$1=$xms $xmx" >> "$params_file"
  else
    if [[ "$params" =~ $initial_heap_size_rx ]]; then
      sed -i -E "/^$1=/s/$initial_heap_size_rx ?//g" "$params_file"
    fi
    sed -i "s/^$1=.*/\0 $xms/" "$params_file"

    if [[ "$params" =~ $max_heap_size_rx ]]; then
      sed -i -E "/^$1=/s/$max_heap_size_rx ?//g" "$params_file"
    fi
    sed -i "s/^$1=.*/\0 $xmx/" "$params_file"
  fi

  local -r updated_params="$(get_params_line "$1")"

  if [ "$params" == "$updated_params" ]; then
    echo "No changes for config line: $updated_params"
  else
    echo "Updated config line: $updated_params"
  fi

}

function to_unit_str() {
  local -r as_bytes="$(to_bytes $1)"

  if [ -z "$as_bytes" ]; then
    echo ""
  else
    local value="$as_bytes"
    local unit=""

    local bytes_in_k="1024"
    local bytes_in_k_half="512"
    local bytes_in_m=$(echo "$bytes_in_k * 1024" | bc)
    local bytes_in_m_half=$(echo "$bytes_in_k_half * 1024" | bc)
    local bytes_in_g=$(echo "$bytes_in_m * 1024" | bc)
    local bytes_in_g_half=$(echo "$bytes_in_m_half * 1024" | bc)

    if [ "$value" -gt "$bytes_in_g" ]; then
      #value=$(($value / $bytes_in_g))
      value=$(echo "scale=0; (($value+$bytes_in_g_half)/$bytes_in_g)" | bc)
      unit="g"
    elif [ "$value" -gt "$bytes_in_m" ]; then
      #value=$(($value / $bytes_in_m))
      value=$(echo "scale=0; (($value+$bytes_in_m_half)/$bytes_in_m)" | bc)
      unit="m"
    elif [ "$value" -gt "$bytes_in_k" ]; then
      #value=$(($value / $bytes_in_k))
      value=$(echo "scale=0; (($value+$bytes_in_k_half)/$bytes_in_k)" | bc)
      unit="k"
    fi

    echo "${value}${unit}"
  fi
}

function find_xms() {
  local -r last_match=$(echo "$1" | grep -o -E "$initial_heap_size_rx" | tail -n 1)

  if [[ "$last_match" =~ $initial_heap_size_rx ]]; then
      echo "${BASH_REMATCH[2]}"
    else
      echo ""
    fi
}

function find_xmx() {
  local -r last_match=$(echo "$1" | grep -o -E "$max_heap_size_rx" | tail -n 1)

  if [[ "$last_match" =~ $max_heap_size_rx ]]; then
      echo "${BASH_REMATCH[2]}"
  else
    echo ""
  fi
}

function apply_percentile() {
  if [ "$1" -gt "1000" ]; then
    die "First argument must be less than or equal to 1000"
  fi
  echo $2*$1/1000 | bc
}

#Returns total memory in megabytes
function get_total_memory() {
  local total_memory=""

  if [ -f /sys/fs/cgroup/memory.max ]; then
    # cgroup v2
    memory_limit=$(cat /sys/fs/cgroup/memory.max)
  elif [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
    # cgroup v1
    memory_limit=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
  fi

  if [[ -z "$memory_limit" || "$memory_limit" == "max" ]]; then
    total_memory="$(free -b | awk '/Mem:/ {print $2}')"
  fi

  echo "$total_memory"
}

#Returns used memory in megabytes
function get_used_memory() {
  echo "$(free -b | awk '/Mem:/ {print $3}')"
}

#Calculates memory based on total memory and given percentiles
#Arguments:
#1. Total memory
#2. Minimum allowed memory in megabytes
#3. Maximum allowed memory in megabytes
#4. global array variable: memory_config. Percentiles list for getting memory based on total memory.
#For example: ("4000:125" "8000:500") means if total memory is less than 4000 then result is 1/8 of total memory, if less than 8000 then half of total memory.
#should be listed in ascending order based on total memory part.
memory_config=()
calculate_recommended_memory() {
  local -r total_mem="$1"
  local -r min_val=$(to_bytes "$2")
  local -r max_val=$(to_bytes "$3")

  local result="$max_val"

  for pair in "${memory_config[@]}"; do

    IFS=':' read -ra config <<< "$pair"
    local limit=$(to_bytes "${config[0]}")
    if [[ "$total_mem" -lt "$limit" ]]; then
      result=$(apply_percentile "${config[1]}" "$total_mem")
      break
    fi
  done

  if [[ "$result" -gt "$max_val" ]]; then
    echo "$max_val"
  elif [[ "$result" -lt "$min_val" ]]; then
      echo "$min_val"
  else
    echo "$(to_unit_str $result)"
  fi
}
