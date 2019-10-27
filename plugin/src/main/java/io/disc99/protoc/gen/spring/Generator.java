package io.disc99.protoc.gen.spring;


import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import io.disc99.protoc.gen.spring.generator.*;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.disc99.protoc.gen.spring.generator.Template.apply;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of {@link ProtocPluginCodeGenerator} that generates Spring Framework-compatible
 * WebFlux handlers and swagger-annotated POJOs for gRPC services.
 */
class Generator extends ProtocPluginCodeGenerator {

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    protected String getPluginName() {
        return "protoc-gen-spring-webflux";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    protected String generatePluginJavaClass(@Nonnull final String protoJavaClass) {
        return protoJavaClass + "Handlers";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    protected String generateImports() {
        return apply("imports", null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    protected Optional<String> generateServiceCode(@Nonnull final ServiceDescriptor serviceDescriptor) {
        final String responseWrapper = serviceDescriptor.getName() + "Response";

        HashMap<String, Object> context = new HashMap<>();
        context.put("serviceName", serviceDescriptor.getName());
        context.put("responseWrapper", responseWrapper);
        context.put("package", serviceDescriptor.getJavaPkgName());
        context.put("packageProto", serviceDescriptor.getProtoPkgName());
        context.put("methods", serviceDescriptor.getMethodDescriptors().stream()
                .map(serviceMethodDescriptor ->
                        new MethodGenerator(serviceDescriptor, serviceMethodDescriptor, responseWrapper, parameters).getMethodContexts())
                .flatMap(Collection::stream)
                .collect(toList()));

        context.put("handleMethodDefinitions", serviceDescriptor.getMethodDescriptors().stream()
                .map(serviceMethodDescriptor ->
                        new MethodGenerator(serviceDescriptor, serviceMethodDescriptor, responseWrapper, parameters).generateHandleCode())
                .collect(toList()));
        String serviceHandler = apply("service_handler", context);

        context.put("proxyMethodDefinitions", serviceDescriptor.getMethodDescriptors().stream()
                .map(serviceMethodDescriptor ->
                        new MethodGenerator(serviceDescriptor, serviceMethodDescriptor, responseWrapper, parameters).generateProxyCode())
                .collect(toList()));
        String serviceProxy = apply("service_proxy", context);

        return Optional.of(serviceHandler + serviceProxy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    protected Optional<String> generateMessageCode(@Nonnull final MessageDescriptor messageDescriptor) {
        final Map<Integer, String> oneofNameMap = new HashMap<>();
        final DescriptorProto descriptorProto = messageDescriptor.getDescriptorProto();
        for (int i = 0; i < descriptorProto.getOneofDeclCount(); ++i) {
            oneofNameMap.put(i, descriptorProto.getOneofDecl(i).getName());
        }

        HashMap<String, Object> context = new HashMap<>();
        context.put("comment", messageDescriptor.getComment());
        context.put("className", messageDescriptor.getName());
        context.put("originalProtoType", messageDescriptor.getQualifiedOriginalName());
        context.put("nestedDefinitions", messageDescriptor.getNestedMessages().stream()
                .filter(nestedDescriptor -> !(nestedDescriptor instanceof MessageDescriptor &&
                        ((MessageDescriptor) nestedDescriptor).isMapEntry()))
                .map(this::generateCode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList()));
        context.put("fieldDeclarations", messageDescriptor.getFieldDescriptors().stream()
                .map(descriptor -> generateFieldDeclaration(descriptor, oneofNameMap))
                .collect(toList()));
        context.put("setBuilderFields", messageDescriptor.getFieldDescriptors().stream()
                .map(this::addFieldToProtoBuilder)
                .collect(toList()));
        context.put("setMsgFields", messageDescriptor.getFieldDescriptors().stream()
                .map(descriptor -> addFieldSetFromProto(descriptor, "newMsg"))
                .collect(toList()));

        return Optional.of(apply("message", context));

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    protected Optional<String> generateEnumCode(@Nonnull final EnumDescriptor enumDescriptor) {
        HashMap<String, Object> context = new HashMap<>();
        context.put("enumName", enumDescriptor.getName());
        context.put("comment", enumDescriptor.getComment());
        context.put("originalProtoType", enumDescriptor.getQualifiedOriginalName());
        context.put("values", enumDescriptor.getValues().entrySet().stream()
                .map(valueEntry -> {
                    HashMap<String, Object> value = new HashMap<>();
                    value.put("comment", enumDescriptor.getValueComment(valueEntry.getKey()));
                    value.put("name", valueEntry.getKey());
                    value.put("value", valueEntry.getValue());
                    return value;
                })
                .collect(toList()));

        return Optional.of(apply("enumerator", context));
    }

    @Nonnull
    private String generateFieldDeclaration(@Nonnull final FieldDescriptor fieldDescriptor,
                                            @Nonnull final Map<Integer, String> oneofNameMap) {
        HashMap<String, Object> context = new HashMap<>();
        context.put("type", fieldDescriptor.getType());
        context.put("displayName", fieldDescriptor.getName());
        context.put("name", fieldDescriptor.getSuffixedName());
        context.put("isRequired", fieldDescriptor.getProto().getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED);
        context.put("comment", fieldDescriptor.getComment());
       if (fieldDescriptor.getProto().hasOneofIndex()) {
            context.put("hasOneOf", true);
            context.put("oneof", FieldDescriptor.formatFieldName(
                    oneofNameMap.get(fieldDescriptor.getProto().getOneofIndex())));
        } else {
            context.put("hasOneOf", false);
        }
        return apply("field_decl", context);
    }

    /**
     * Generate code to add this field to the builder that creates
     * a protobuf object from the generated Java object.
     *
     * @return The generated code string.
     */
    @Nonnull
    private String addFieldToProtoBuilder(@Nonnull final FieldDescriptor fieldDescriptor) {
        HashMap<String, Object> context = new HashMap<>();
        context.put("name", fieldDescriptor.getSuffixedName());
        context.put("capProtoName", StringUtils.capitalize(fieldDescriptor.getName()));
        context.put("isList", fieldDescriptor.isList());
        context.put("isMsg", fieldDescriptor.getContentMessage().isPresent());
        context.put("isMap", fieldDescriptor.isMapField());;

        if (fieldDescriptor.isMapField()) {
            FieldDescriptor value = fieldDescriptor.getContentMessage()
                    .map(descriptor -> ((MessageDescriptor) descriptor).getMapValue())
                    .orElseThrow(() -> new IllegalStateException("Content message not present in map field."));

            context.put("isMapMsg", value.getContentMessage().isPresent());

        }
        return apply("field_to_proto_builder", context);
    }

    /**
     * Generate code to set this field in the generated Java
     * object from a protobuf object.
     *
     * @param msgName The variable name of the protobuf object.
     * @return The generated code string.
     */
    @Nonnull
    private String addFieldSetFromProto(@Nonnull final FieldDescriptor fieldDescriptor,
                                        @Nonnull final String msgName) {
        HashMap<String, Object> context = new HashMap<>();

        context.put("isMsg", fieldDescriptor.getContentMessage().isPresent());
        context.put("isProto3", fieldDescriptor.isProto3Syntax());
        context.put("msgName", msgName);
        context.put("fieldName", fieldDescriptor.getSuffixedName());
        context.put("fieldNumber", fieldDescriptor.getProto().getNumber());
        context.put("capProtoName", StringUtils.capitalize(fieldDescriptor.getName()));
        context.put("msgType", fieldDescriptor.getTypeName());
        context.put("isList", fieldDescriptor.isList());
        context.put("isMap", fieldDescriptor.isMapField());
        context.put("isOneOf", fieldDescriptor.getOneofName().isPresent());

        fieldDescriptor.getOneofName().ifPresent(oneOfName ->
                context.put("oneOfName", StringUtils.capitalize(oneOfName)));

        if (fieldDescriptor.isMapField()) {
            FieldDescriptor value = fieldDescriptor.getContentMessage()
                    .map(descriptor -> ((MessageDescriptor) descriptor).getMapValue())
                    .orElseThrow(() -> new IllegalStateException("Content message not present in map field."));
            context.put("isMapMsg", value.getContentMessage().isPresent());
            context.put("mapValType", value.getTypeName());
        }

        return apply("field_from_proto", context);
    }
}
