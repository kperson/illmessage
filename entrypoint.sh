#!/bin/bash

set -x

if [ "$1" = "background" ]; then
    java -Xmx768m -jar /jars/background-assembly-1.0.0.jar ${@:2}
elif [ "$1" = "app" ]; then
    java -Xmx768m -jar /jars/app-assembly-1.0.0.jar
else
    exec "$@"
fi