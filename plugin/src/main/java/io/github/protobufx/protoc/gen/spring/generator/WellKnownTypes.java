package io.github.protobufx.protoc.gen.spring.generator;

public enum WellKnownTypes {
    FIELD_MASK(
            ".google.protobuf.FieldMask",
            "com.google.protobuf.FieldMask",
            "String"
    ),
    TIMESTAMP(
            ".google.protobuf.Timestamp",
            "com.google.protobuf.Timestamp",
            "String"
    ),
    DURATION(
            ".google.protobuf.Duration",
            "com.google.protobuf.Duration",
            "String"
    ),
    ;

    WellKnownTypes(String typeName, String className, String mapping) {
        this.typeName = typeName;
        this.className = className;
        this.mapping = mapping;
    }

    String typeName;
    String className;
    String mapping;

    public String typeName() {
        return typeName;
    }

    public String className() {
        return className;
    }

    public String mapping() {
        return mapping;
    }
}
