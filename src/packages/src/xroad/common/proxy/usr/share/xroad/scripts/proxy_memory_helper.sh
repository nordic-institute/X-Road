#!/bin/bash

source /usr/share/xroad/scripts/_setup_memory.sh

usage()
{
cat << EOF
usage: $0 <action>

Check and update memory settings for proxy service.

OPTIONS:
   action  Action to execute, supported values:
       status - display current state: total memory, used memory, current memory configuration for proxy
       get-default - displays default memory configuration for proxy service
       get-recommended - displays recommended memory configuration for proxy service based on total memory
       apply-default - applies default memory configuration for proxy service
       apply-recommended - applies recommended memory configuration for proxy service based on total memory
       apply - applies custom memory config, requires 2 arguments: min and max memory, for example: $0 apply 512m 5g

EOF
}

default_xms="100m"
default_xmx="512m"

verify_config(){
  local -r xms=$(to_bytes "$1")
  local -r xmx=$(to_bytes "$2")

  if [ -z "$xms" ]; then
    die "Invalid first argument. Must be in <number>[k|m|g] format, for example 128m"
  fi

  if [ -z "$xmx" ]; then
    die "Invalid second argument. Must be in <number>[k|m|g] format, for example 3g"
  fi

  if [ "$xms" -ge "$xmx" ]; then
    die "First argument should be smaller than second"
  fi
}

find_used_by_proxy(){
  echo "$(ps -u xroad -o %mem,args | awk '/ProxyMain/ {print $1}')%"
}

get_current_xms(){
  local -r ps_args="$(ps -u xroad -o args | grep ProxyMain)"
  local -r local_params="$(get_params_line 'XROAD_PROXY_PARAMS')"

  local xms=$(find_xms "$local_params")

  if [ -z "$xms" ]; then
      xms=$(find_xms "$ps_args")
  fi

  echo "$xms"
}

get_current_xmx(){
  local -r ps_args="$(ps -u xroad -o args | grep ProxyMain)"
  local -r local_params="$(get_params_line 'XROAD_PROXY_PARAMS')"

  local xmx=$(find_xmx "$local_params")

  if [ -z "$xmx" ]; then
      xmx=$(find_xmx "$ps_args")
  fi

  echo "$xmx"
}

get_recommended_xms(){
  local -r total_mem=$(get_total_memory)

  memory_config=("4g:49" "8g:63" "16g:125")
  echo $(calculate_recommended_memory "$total_mem" "100m" "2g")
}

get_recommended_xmx(){
  local -r total_mem=$(get_total_memory)

  memory_config=("4g:125" "8g:250" "16g:500" "31g:52")
  echo $(calculate_recommended_memory "$total_mem" "512m" "16g")
}

display_status(){
  local -r total_memory=$(get_total_memory)
  local -r used_memory=$(get_used_memory)
  local -r used_by_proxy=$(find_used_by_proxy)
  local -r current_xms=$(get_current_xms)
  local -r current_xmx=$(get_current_xmx)
  local -r recommended_xms=$(get_recommended_xms)
  local -r recommended_xmx=$(get_recommended_xmx)

  local -r total_memory_str=$(to_unit_str "$total_memory")
  local -r used_memory_str=$(($used_memory * 100 / total_memory))

cat << EOF
Status:
  Total memory: ${total_memory_str}
  Used: ${used_memory_str}%
  Used by proxy service: ${used_by_proxy}

  Current proxy service memory config: ${current_xms} - ${current_xmx}

  Default config: ${default_xms} - ${default_xmx}
  (Apply default config with '$0 apply-default')

  Recommended config based on total memory: ${recommended_xms} - ${recommended_xmx}
  (Apply recommended config with '$0 apply-recommended')

EOF

}

if [[ -z "$1" || "$1" == "status" ]]; then
  display_status
elif [ "$1" == "get-current" ]; then
  echo  "$(get_current_xms) $(get_current_xmx)"
elif [ "$1" == "get-default" ]; then
  echo "-Xms$default_xms -Xmx$default_xmx"
elif [ "$1" == "get-recommended" ]; then
  echo  "-Xms$(get_recommended_xms) -Xmx$(get_recommended_xmx)"
elif [ "$1" == "apply-default" ]; then
  apply_memory_config "XROAD_PROXY_PARAMS" "$default_xms" "$default_xmx"
elif [ "$1" == "apply-recommended" ]; then
  apply_memory_config "XROAD_PROXY_PARAMS" "$(get_recommended_xms)" "$(get_recommended_xmx)"
elif [ "$1" == "apply" ]; then
  verify_config "$2" "$3"
  apply_memory_config "XROAD_PROXY_PARAMS" "$2" "$3"
elif [ "$1" == "help" ]; then
  usage
else
    die "Unknown action. Use 'help' to get available actions"
fi


