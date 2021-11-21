#!/bin/bash

COMMAND=$1

echo '>>> Init'
rm -rf ~/.m2/repository/io/protobufx/protoc-gen-spring-webflux/local

if [ "$COMMAND" = '-t' ]; then
  echo '>>> Test'
  ./gradlew clean plugin:publishToMavenLocal example:generateProto example:test --stacktrace
fi

if [ "$COMMAND" = '-r' ]; then
  echo '>>> Release'
  ./gradlew publish
fi
