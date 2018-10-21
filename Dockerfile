FROM kperson/alpine-java-8

ADD . /code
WORKDIR /code
RUN sbt assembly

ADD entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]