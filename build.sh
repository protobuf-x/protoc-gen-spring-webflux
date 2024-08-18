#!/bin/bash

COMMAND=$1

echo '>>> Init'
rm -rf ~/.m2/repository/io/protobufx/protoc-gen-spring-webflux/local

if [ "$COMMAND" = '-g' ]; then
  echo '>>> Generate'
  ./gradlew clean plugin:publishToMavenLocal example:generateProto
fi

if [ "$COMMAND" = '-t' ]; then
  echo '>>> Test'
  ./gradlew clean plugin:publishToMavenLocal example:generateProto example:test --stacktrace
fi

if [ "$COMMAND" = '-r' ]; then
  echo '>>> Release'
  ./gradlew publish
fi

if [ "$COMMAND" = '-b' ]; then
  echo '>>> buf'
  ./gradlew clean plugin:publishToMavenLocal
  cp plugin/build/libs/protoc-gen-spring-webflux-local.jar example/src/main/buf
  cd example/src/main/buf
  buf generate
fi
