package io.protobufx.protoc.gen.spring;

import com.google.api.HttpRule;
import com.google.common.base.CaseFormat;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.TextFormat;
import io.protobufx.protoc.gen.spring.generator.*;
import io.protobufx.protoc.gen.spring.generator.ServiceMethodDescriptor.MethodType;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static io.protobufx.protoc.gen.spring.generator.Template.apply;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * A utility class to encapsulate the generation of HTTP methods for gRPC services.
 */
public class MethodGenerator {

    private static final Logger log = LogManager.getLogger();

    private final ServiceDescriptor serviceDescriptor;

    private final ServiceMethodDescriptor serviceMethodDescriptor;

    private final String responseWrapper;


    public MethodGenerator(@Nonnull final ServiceDescriptor serviceDescriptor,
                           @Nonnull final ServiceMethodDescriptor serviceMethodDescriptor,
                           @Nonnull final String responseWrapper) {
        this.serviceDescriptor = Objects.requireNonNull(serviceDescriptor);
        this.serviceMethodDescriptor = Objects.requireNonNull(serviceMethodDescriptor);
        this.responseWrapper = Objects.requireNonNull(responseWrapper);
    }

    @Nonnull
    public List<Map<String, Object>> getMethodContexts() {
        return getMethodTemplates().stream()
                .map(MethodTemplate::getContext)
                .collect(toList());
    }

    private List<MethodTemplate> getMethodTemplates() {
        if (!serviceMethodDescriptor.getHttpRule().isPresent()) {
            return Collections.emptyList();
        }

        final HttpRule topLevelRule = serviceMethodDescriptor.getHttpRule().get();
        final List<MethodTemplate> allMethodsContexts = new ArrayList<>();
        generateMethodFromHttpRule(topLevelRule, null).ifPresent(allMethodsContexts::add);

        // No recursion allowed - additional bindings will not contain rules that contain
        // additional bindings!
        for (int i = 0; i < topLevelRule.getAdditionalBindingsCount(); ++i) {
            generateMethodFromHttpRule(topLevelRule.getAdditionalBindings(i), i)
                    .ifPresent(allMethodsContexts::add);
        }
        return allMethodsContexts;
    }

    @Nonnull
    private Optional<MethodTemplate> generateMethodFromHttpRule(@Nonnull final HttpRule httpRule,
                                                      @Nullable final Integer bindingIndex) {
        switch (httpRule.getPatternCase()) {
            case GET:
                return Optional.of(generateMethodCode(httpRule.getGet(), null,
                        httpRule.getPatternCase(), bindingIndex));
            case PUT:
                return Optional.of(generateMethodCode(httpRule.getPut(), httpRule.getBody().isEmpty() ? null : httpRule.getBody(),
                        httpRule.getPatternCase(), bindingIndex));
            case POST:
                return Optional.of(generateMethodCode(httpRule.getPost(), httpRule.getBody().isEmpty() ? null : httpRule.getBody(),
                        httpRule.getPatternCase(), bindingIndex));
            case DELETE:
                return Optional.of(generateMethodCode(httpRule.getDelete(), httpRule.getBody().isEmpty() ? null : httpRule.getBody(),
                        httpRule.getPatternCase(), bindingIndex));
            case PATCH:
                return Optional.of(generateMethodCode(httpRule.getPatch(), httpRule.getBody().isEmpty() ? null : httpRule.getBody(),
                        httpRule.getPatternCase(), bindingIndex));
            case CUSTOM:
                log.error("Custom HTTP Rule Pattern Not Supported!\n {}",
                        TextFormat.printToString(httpRule.getCustom()));
                return Optional.empty();
        }
        return Optional.empty();
    }

    @Nonnull
    private MethodTemplate generateMethodCode(@Nonnull final String pattern,
                                              @Nullable final String bodyPattern,
                                              @Nonnull final HttpRule.PatternCase httpMethod,
                                              @Nullable final Integer bindingIndex) {
        final PathTemplate template = new PathTemplate(pattern);
        final MessageDescriptor inputDescriptor = serviceMethodDescriptor.getInputMessage();
        final Set<String> boundVariables = template.getBoundVariables();

        final MethodGenerationFieldVisitor fieldVisitor = new MethodGenerationFieldVisitor(boundVariables);

        // We visit all fields. There are two possibilities:
        // 1) Field is "bound" to the path.
        // 2) Field is not bound. It may become a query parameter, or be in the request body.
        inputDescriptor.visitFields(fieldVisitor);

        final List<String> requestToInputSteps = new ArrayList<>();

        if (bodyPattern != null) {
            final String body = StringUtils.strip(bodyPattern);
            if (body.equals("*")) {
                requestToInputSteps.add(".flatMap(inputBuilder -> {\n" +
                        "                return serverRequest\n" +
                        "                        .bodyToMono(DataBuffer.class)\n" +
                        "                        .map(dataBuffer -> {\n" +
                        "                            mergeJson(dataBuffer, inputBuilder);\n" +
                        "                            return inputBuilder;\n" +
                        "                        });\n" +
                        "              })");
            } else {
                if (body.contains(".")) {
                    throw new IllegalArgumentException("Invalid body: " + body + ". Body must refer to a top-level field.");
                }

                // Must be a field of the top-level request object.
                final FieldDescriptor bodyField = inputDescriptor.getFieldDescriptors().stream()
                        .filter(fieldDescriptor -> fieldDescriptor.getProto().getName().equals(body))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Body field: "
                                + body + " does not exist. Valid fields are: " +
                                inputDescriptor.getFieldDescriptors().stream()
                                        .map(FieldDescriptor::getName)
                                        .collect(joining(", "))));
                if (bodyField.isList() || bodyField.isMapField()) {
                    throw new IllegalArgumentException("Invalid body: " + body + ". Body must refer to a non-repeated/map field.");
                }

                requestToInputSteps.add(".flatMap(\n" +
                        "                inputBuilder ->\n" +
                        "                    serverRequest\n" +
                        "                        .bodyToMono(DataBuffer.class)\n" +
                        "                        .filter(Objects::nonNull)\n" +
                        "                            .map(\n" +
                        "                                    dataBuffer -> {\n" +
                        "                                        " + bodyField.getContentMessage().get().getQualifiedOriginalName() + ".Builder builder = "+bodyField.getContentMessage().get().getQualifiedOriginalName()+".newBuilder();\n" +
                        "                                        mergeJson(dataBuffer, builder);\n" +
                        "                                        "+ generateVariableSetterBody(body)+"\n" +
                        "                                    }))\n");

            }
        } else {
            fieldVisitor.getQueryParamFields().forEach((path, type) -> {
                Map<String, Object> context = new HashMap<>();
                String typeName = type.getContentMessage()
                        .map(AbstractDescriptor::getQualifiedOriginalName)
                        .orElse(type.getTypeName());
                context.put("convert", convertString("p", typeName));
                context.put("type", typeName);
                context.put("variable", variableForPath(path));
                context.put("variableSetter", generateVariableSetterParam(path, type));
                requestToInputSteps.add(apply("flatmap_variable_query_parameter", context));
            });
        }

        fieldVisitor.getPathFields().forEach((path, type) -> {
            Map<String, Object> context = new HashMap<>();
//            context.put("convert", convertString("serverRequest.pathVariable(\"" + variableForPath(path) + "\")", type.getType()));
            context.put("convert", convertString("p", type.getType()));
            context.put("type", type.getType());
            context.put("variable", variableForPath(path));
            context.put("variableSetter", generateVariableSetterPath(path, type));
            requestToInputSteps.add(apply("flatmap_variable_path", context));
        });

        final StringBuilder requestToInput = new StringBuilder()
                .append("Mono.just(")
                .append(inputDescriptor.getQualifiedOriginalName())
                .append(".newBuilder())");

        requestToInputSteps.forEach(requestToInput::append);

        requestToInput.append(".map(")
                .append(inputDescriptor.getQualifiedOriginalName())
                .append(".Builder::build)");

        String index = bindingIndex == null ? "" : Integer.toString(bindingIndex);
        String restMethodName = StringUtils.uncapitalize(serviceMethodDescriptor.getName()) + index;
        return new MethodTemplate(httpMethod)
                .setPath(template.getQueryPath())
                .setIsRequestJson(bodyPattern != null)
                .setRequestToInput(requestToInput.toString())
                .setRestMethodName(restMethodName)
                .setRestPathField(lowerCamelToUpperSnake(restMethodName) + "_PATH");
    }

    @Nonnull
    private String generateVariableSetterParam(@Nonnull final String path,
                                          @Nonnull final FieldDescriptor field) {
        final StringBuilder setFieldBuilder = new StringBuilder();
        final Deque<String> pathStack = new ArrayDeque<>(Arrays.asList(path.split("\\.")));

        if (!isRepeated(field)) {
            setFieldBuilder.append("if (!")
                    .append(variableForPath(path))
                    .append(".isEmpty()) {")
                    .append("inputBuilder.set")
                    .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                    .append("(")
                    .append(variableForPath(path))
                    .append(".get(0)");
            setFieldBuilder.append(");")
                    .append("}");
        } else {
            setFieldBuilder.append(" inputBuilder");

            while (pathStack.size() > 1) {
                setFieldBuilder.append(".get")
                        .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                        .append("Builder()");
            }

            setFieldBuilder.append(".addAll")
                    .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                    .append("(")
                    .append(variableForPath(path));
            setFieldBuilder.append(");");
        }

        setFieldBuilder.append("return inputBuilder;");

        return setFieldBuilder.toString();
    }

    @Nonnull
    private String generateVariableSetterPath(@Nonnull final String path,
                                          @Nonnull final FieldDescriptor field) {
        final StringBuilder setFieldBuilder = new StringBuilder();
        final Deque<String> pathStack = new ArrayDeque<>(Arrays.asList(path.split("\\.")));

        setFieldBuilder.append(" inputBuilder");
        while (pathStack.size() > 1) {
            setFieldBuilder.append(".get")
                    .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                    .append("Builder()");
        }

        setFieldBuilder.append(".set")
                .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                .append("(")
                .append(variableForPath(path));
        setFieldBuilder.append(")");

        return setFieldBuilder.toString();
    }

    @Nonnull
    private String generateVariableSetterBody(@Nonnull final String path) {
        final StringBuilder setFieldBuilder = new StringBuilder();
        final Deque<String> pathStack = new ArrayDeque<>(Arrays.asList(path.split("\\.")));

        setFieldBuilder
                .append("inputBuilder.set")
                .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                .append("(builder.build());")
                .append("return inputBuilder;");

        return setFieldBuilder.toString();
    }

    @Nonnull
    private boolean isMessageOrEnum(@Nonnull FieldDescriptor field) {
        return field.getProto().getType().equals(Type.TYPE_MESSAGE) || field.getProto().getType().equals(Type.TYPE_ENUM);
    }

    private boolean isRepeated(@Nonnull FieldDescriptor field) {
        return field.getProto().getLabel().equals(Label.LABEL_REPEATED);
    }

    @Nonnull
    private String variableForPath(@Nonnull final String path) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, path.replace(".", "_"));
    }

    @Nonnull
    private String lowerSnakeToUpperCamel(String value) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, value);
    }

    @Nonnull
    private String lowerCamelToUpperSnake(String value) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, value);
    }

    private class MethodTemplate {
        private final Map<String, Object> context;

        MethodTemplate(@Nonnull final HttpRule.PatternCase httpMethod) {
            final MessageDescriptor inputDescriptor = serviceMethodDescriptor.getInputMessage();
            final MessageDescriptor outputDescriptor = serviceMethodDescriptor.getOutputMessage();
            final MethodType type = serviceMethodDescriptor.getType();
            final String requestBodyType = getBodyType(true);
            final String protoInputType = getProtoInputType();
            final String responseBodyType = responseWrapper + "<" + getBodyType(false) + ">";

            context = new HashMap<>();
            context.put("resultProto", outputDescriptor.getQualifiedOriginalName());
            context.put("resultType", outputDescriptor.getQualifiedName());
            context.put("responseWrapper", responseWrapper);
            context.put("requestProto", inputDescriptor.getQualifiedOriginalName());
            context.put("protoInputType", protoInputType);
            context.put("requestType", inputDescriptor.getQualifiedName());
            context.put("responseBodyType", responseBodyType);
            context.put("isClientStream", type == MethodType.BI_STREAM || type == MethodType.CLIENT_STREAM);
            context.put("isSingleResponse", type == MethodType.SIMPLE || type == MethodType.CLIENT_STREAM);
            context.put("comments", serviceMethodDescriptor.getComment());
            context.put("methodName", StringUtils.uncapitalize(serviceMethodDescriptor.getName()));
            context.put("methodProto", serviceMethodDescriptor.getName());
            context.put("methodTypeName", httpMethod.name());
            context.put("serviceName", serviceDescriptor.getName());
        }

        @Nonnull
        public MethodTemplate setRequestToInput(@Nonnull final String requestToInputCode) {
            this.context.put("prepareInput", requestToInputCode);
            return this;
        }

        @Nonnull
        public MethodTemplate setPath(@Nonnull final String path) {
            this.context.put("path", path);
            return this;
        }

        @Nonnull
        public MethodTemplate setIsRequestJson(final boolean isRequestJson) {
            this.context.put("isRequestJson", isRequestJson);
            return this;
        }

        @Nonnull
        public MethodTemplate setRestMethodName(@Nonnull final String restMethodName) {
            this.context.put("restMethodName", restMethodName);
            return this;
        }

        public MethodTemplate setRestPathField(@Nonnull final String restPathField) {
            this.context.put("restPathField", restPathField);
            return this;
        }

        @Nonnull
        public Map<String, Object> getContext() {
            return context;
        }
    }

    @Nonnull
    private String getBodyType(final boolean request) {
        final MessageDescriptor descriptor = request ?
                serviceMethodDescriptor.getInputMessage() : serviceMethodDescriptor.getOutputMessage();
        return descriptor.getQualifiedName();
    }

    private String getProtoInputType() {
        final MessageDescriptor descriptor = serviceMethodDescriptor.getInputMessage();
        return descriptor.getQualifiedOriginalName();
    }

    static class MethodGenerationFieldVisitor implements MessageDescriptor.MessageFieldVisitor {
        private final Deque<String> path;
        private final Set<String> boundVariables;
        private final Map<String, FieldDescriptor> queryParamsWithType = new HashMap<>();
        private final Map<String, FieldDescriptor> boundVarsWithType = new HashMap<>();

        public MethodGenerationFieldVisitor(final Set<String> boundVariables) {
            this.boundVariables = boundVariables;
            path = new ArrayDeque<>();
        }

        @Nonnull
        public Map<String, FieldDescriptor> getQueryParamFields() {
            return queryParamsWithType;
        }

        @Nonnull
        public Map<String, FieldDescriptor> getPathFields() {
            return boundVarsWithType;
        }

        @Override
        public boolean startMessageField(@Nonnull final FieldDescriptor field, @Nonnull final MessageDescriptor messageType) {
            path.push(field.getProto().getName());
            return true;
        }

        @Override
        public void endMessageField(@Nonnull final FieldDescriptor field, @Nonnull final MessageDescriptor messageType) {
            path.pop();
        }

        @Override
        public void visitBaseField(@Nonnull final FieldDescriptor field) {
            final String fullPath = getFullPath(field);
            if (!boundVariables.contains(fullPath)) {
                queryParamsWithType.put(fullPath, field);
            } else {
                boundVarsWithType.put(fullPath, field);
            }
        }

        @Override
        public void visitEnumField(@Nonnull final FieldDescriptor field, @Nonnull final EnumDescriptor enumDescriptor) {
            final String fullPath = getFullPath(field);
            if (!boundVariables.contains(fullPath)) {
                queryParamsWithType.put(fullPath, field);
            } else {
                boundVarsWithType.put(fullPath, field);
            }
        }

        @Nonnull
        private String getFullPath(@Nonnull final FieldDescriptor field) {
            if (path.isEmpty()) {
                return field.getProto().getName();
            }
            return String.join(".", path) + "." + field.getProto().getName();
        }
    }

    @Nonnull
    private String convertString(String value, String type) {
        switch (type) {
            case "String":
                return value;
            case "Boolean":
                return "Boolean.valueOf(" + value + ")";
            case "Byte":
                return "Byte.valueOf(" + value + ")";
            case "Short":
                return "Short.valueOf(" + value + ")";
            case "Integer":
                return "Integer.valueOf(" + value + ")";
            case "Long":
                return "Long.valueOf(" + value + ")";
            case "Float":
                return "Float.valueOf(" + value + ")";
            case "Double":
                return "Double.valueOf(" + value + ")";
            default:
                // Enum#valueOf
                return type + ".valueOf(" + value + ")";
        }
    }
}
