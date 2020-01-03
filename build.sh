#!/bin/bash

COMMAND=$1

echo '>>> init'
rm -rf ~/.m2/repository/io/protobufx/protoc-gen-spring-webflux/local
export JAVA_HOME=$(/System/Library/Frameworks/JavaVM.framework/Versions/A/Commands/java_home -v "1.8")


if [ "$COMMAND" = '-t' ]; then
  echo '>>> Test'
  ./gradlew clean plugin:publishToMavenLocal example:generateProto example:test --stacktrace
fi

if [ "$COMMAND" = '-r' ]; then
  echo '>>> Release'
  ./gradlew clean plugin:bintrayUpload
fi

