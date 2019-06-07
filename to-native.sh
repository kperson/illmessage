#!/bin/bash

#https://blog.playframework.com/play-on-graal/
#https://medium.com/@mathiasdpunkt/fighting-cold-startup-issues-for-your-kotlin-lambda-with-graalvm-39d19b297730
docker run --rm --name graal -v $(pwd)/app/target/scala-2.12:/working oracle/graalvm-ce:1.0.0-rc16 \
    /bin/bash -c "native-image --enable-url-protocols=http \
                    -Djava.net.preferIPv4Stack=true \
                    -H:+ReportExceptionStackTraces \
                    -H:+ReportUnsupportedElementsAtRuntime --no-server -jar /working/app-assembly-1.0.0.jar \
                    ; \
                    cp app-assembly-1.0.0 /working/server"
