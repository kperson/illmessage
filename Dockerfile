FROM kperson/alpine-java-8

ADD . /code
WORKDIR /code
RUN sbt 'project app' assembly

CMD ["start.sh"]