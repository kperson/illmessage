FROM kperson/alpine-java-8

ADD . /code
WORKDIR /code
RUN sbt assembly && rm -rf /root/.ivy2 && rm -rf /root/.sbt

ADD entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
CMD ["app"]