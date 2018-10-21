FROM kperson/alpine-java-8

ADD . /code
WORKDIR /code
RUN sbt assembly

ENTRYPOINT ["start.sh"]