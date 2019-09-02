#!/usr/bin/env bash

export JAVA_HOME=$(/System/Library/Frameworks/JavaVM.framework/Versions/A/Commands/java_home -v "1.8")

./gradlew clean shadowJar
protoc --plugin=./protoc-gen-spring-webflux --spring-webflux_out=. example.proto
