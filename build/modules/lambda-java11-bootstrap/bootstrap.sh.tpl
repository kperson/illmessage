#!/bin/sh

/opt/java-dist/bin/java -cp "${jar_file}:/opt/scala_2_12_8/scala-library-2.12.8.jar" $$MAIN_CLASS
