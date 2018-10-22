#!/bin/bash

set -x

if [ "$1" = "background" ]; then
    available_memory=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
    ratio=${JAVA_MAX_MEM_RATIO:-85}
    java_mem=$(echo "${available_memory} ${ratio} 1048576" | awk '{printf "%d\n" , ($1*$2)/(100*$3) + 0.5}')
    java -Xmx${java_mem}m -jar /jars/background-assembly-1.0.0.jar ${@:2}
elif [ "$1" = "app" ]; then
    available_memory=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
    ratio=${JAVA_MAX_MEM_RATIO:-85}
    java_mem=$(echo "${available_memory} ${ratio} 1048576" | awk '{printf "%d\n" , ($1*$2)/(100*$3) + 0.5}')
    java -Xmx${java_mem}m -jar /jars/app-assembly-1.0.0.jar
else
    exec "$@"
fi