FROM kperson/ubuntu-java-11

ADD . /code
WORKDIR /code
RUN mkdir -p /jars

RUN sbt clean cleanFiles assembly \
    && cp /code/app/target/scala-2.12/app-assembly-1.0.0.jar /jars/app-assembly-1.0.0.jar \
    && sbt clean cleanFiles \
    && rm -rf /root/.ivy2 \
    && rm -rf /root/.sbt

ADD entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
CMD ["app"]
