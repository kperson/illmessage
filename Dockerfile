FROM kperson/alpine-java-8

RUN sbt 'project app' assembly