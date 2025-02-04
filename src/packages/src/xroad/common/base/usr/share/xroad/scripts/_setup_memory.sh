#!/bin/bash

die () {
  echo >&2 "$@"
  exit 1
}

function to_megabytes() {
  if [[ $1 =~ ^([0-9]+)m$ ]]; then
    echo "${BASH_REMATCH[1]}"
  elif [[ $1 =~ ^([0-9]+)g$ ]]; then
    echo "${BASH_REMATCH[1]} * 1024" | bc
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
    local pattern="-Xms[0-9]+[mg]"
    if [[ "$params" =~ $pattern ]]; then
      sed -i -E "/^$1=/s/$pattern/$xms/" "$params_file"
    else
      sed -i "s/^$1=.*/\0 $xms/" "$params_file"
    fi

    local pattern="-Xmx[0-9]+[mg]"
    if [[ "$params" =~ $pattern ]]; then
      sed -i -E "/^$1=/s/$pattern/$xmx/" "$params_file"
    else
      sed -i "s/^$1=.*/\0 $xmx/" "$params_file"
    fi
  fi

  local -r updated_params="$(get_params_line "$1")"

  if [ "$params" == "$updated_params" ]; then
    echo "No changes for config line: $updated_params"
  else
    echo "Updated config line: $updated_params"
  fi

}

function to_gigabytes_str() {
  local mb=$1
  local gb=""
  if [[ $1 =~ ^([0-9]+)g$ ]]; then
   gb="${BASH_REMATCH[1]}"
  elif [[ $1 =~ ^([0-9]+)m$ ]]; then
   mb="${BASH_REMATCH[1]}"
  fi

  if [[ -z "$gb" && "$mb" -lt "1024" ]]; then
    echo "${mb}m"
  elif [[ -z "$gb" && "$mb" -ge "1024" ]]; then
    gb=$(($mb / 1024))
    echo "${gb}g"
  else
    echo "${gb}g"
  fi
}

function find_xms() {
  if [[ "$1" =~ -Xms([0-9]+[mg]) ]]; then
      echo "${BASH_REMATCH[1]}"
    else
      echo ""
    fi
}

function find_xmx() {
  if [[ "$1" =~ -Xmx([0-9]+[mg]) ]]; then
    echo "${BASH_REMATCH[1]}"
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
    total_memory=$(free --mega | awk '/Mem:/ {print $2}')
  else
    total_memory=$(($memory_limit / 1024 / 1024))
  fi

  echo "$total_memory"
}

#Returns used memory in megabytes
function get_used_memory() {
  echo "$(free --mega | awk '/Mem:/ {print $3}')"
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
  local -r min_val=$(to_megabytes "$2")
  local -r max_val=$(to_megabytes "$3")

  local result="$max_val"

  for pair in "${memory_config[@]}"; do

    IFS=':' read -ra config <<< "$pair"
    local limit=$(to_megabytes "${config[0]}")
    if [[ "$total_mem" -lt "$limit" ]]; then
      result=$(apply_percentile "${config[1]}" "$total_mem")
      break
    fi
  done

  if [[ "$result" -gt "$max_val" ]]; then
    result="$max_val"
  fi

  if [[ "$result" -lt "$min_val" ]]; then
      result="$min_val"
  fi

  if [[ "$result" -ge "2048" ]]; then
    #round it up to nearest gb
    result=$(echo "scale=0; (($result+512)/1024)" | bc)
    echo "${result}g"
  else
    echo "${result}m"
  fi
}
