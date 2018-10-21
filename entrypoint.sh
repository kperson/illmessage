#!/bin/bash

set -x

if [ "$1" = "background" ]; then
    available_memory=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
    ratio=${JAVA_MAX_MEM_RATIO:-85}
    java_mem=$(echo "${available_memory} ${ratio} 1048576" | awk '{printf "%d\n" , ($1*$2)/(100*$3) + 0.5}')
    java -jar /code/background/target/scala-2.12/background-assembly-1.0.0.jar -Xmx${java_mem}m ${@:2}
elif [ "$1" = "app" ]; then
    available_memory=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)
    ratio=${JAVA_MAX_MEM_RATIO:-85}
    java_mem=$(echo "${available_memory} ${ratio} 1048576" | awk '{printf "%d\n" , ($1*$2)/(100*$3) + 0.5}')
    java -jar /code/app/target/scala-2.12/app-assembly-1.0.0.jar -Xmx${java_mem}m
else
    exec "$@"
fi