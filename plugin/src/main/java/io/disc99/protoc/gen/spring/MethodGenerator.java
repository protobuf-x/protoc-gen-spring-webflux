package io.disc99.protoc.gen.spring;

import com.google.api.HttpRule;
import com.google.common.base.CaseFormat;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.TextFormat;
import io.disc99.protoc.gen.spring.generator.*;
import io.disc99.protoc.gen.spring.generator.ServiceMethodDescriptor.MethodType;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.*;

import static io.disc99.protoc.gen.spring.generator.Template.apply;
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

    private final Parameters parameters;

    public MethodGenerator(@Nonnull final ServiceDescriptor serviceDescriptor,
                           @Nonnull final ServiceMethodDescriptor serviceMethodDescriptor,
                           @Nonnull final String responseWrapper,
                           @Nonnull final Parameters parameters) {
        this.serviceDescriptor = Objects.requireNonNull(serviceDescriptor);
        this.serviceMethodDescriptor = Objects.requireNonNull(serviceMethodDescriptor);
        this.responseWrapper = Objects.requireNonNull(responseWrapper);
        this.parameters = Objects.requireNonNull(parameters);
    }

    @Nonnull
    public String generateHandleCode() {
        return getMethodTemplates().stream()
                .map(MethodTemplate::renderHandle)
                .collect(joining());
    }

    @Nonnull
    public String generateProxyCode() {
        return getMethodTemplates().stream()
                .map(MethodTemplate::renderProxy)
                .collect(joining());
    }

    @Nonnull
    public List<Map<String, Object>> getMethodContexts() {
        return getMethodTemplates().stream()
                .map(MethodTemplate::getContext)
                .collect(toList());
    }

    private List<MethodTemplate> getMethodTemplates() {
        if (!serviceMethodDescriptor.getHttpRule().isPresent()) {
            return Collections.singletonList(new MethodTemplate(SpringMethodType.POST));
        }

        final HttpRule topLevelRule = serviceMethodDescriptor.getHttpRule().get();
        final List<MethodTemplate> allMethodsContexts = new ArrayList<>();
        allMethodsContexts.add(generateMethodFromHttpRule(topLevelRule, Optional.empty()));

        // No recursion allowed - additional bindings will not contain rules that contain
        // additional bindings!
        for (int i = 0; i < topLevelRule.getAdditionalBindingsCount(); ++i) {
            allMethodsContexts.add(
                    generateMethodFromHttpRule(topLevelRule.getAdditionalBindings(i), Optional.of(i)));
        }
        return allMethodsContexts;
    }

    @Nonnull
    private MethodTemplate generateMethodFromHttpRule(@Nonnull final HttpRule httpRule,
                                                      @Nonnull final Optional<Integer> bindingIndex) {
        switch (httpRule.getPatternCase()) {
            case GET:
                return generateMethodCode(httpRule.getGet(), Optional.empty(),
                        SpringMethodType.GET, bindingIndex);
            case PUT:
                return generateMethodCode(httpRule.getPut(), httpRule.getBody().isEmpty() ?
                                Optional.empty() : Optional.of(httpRule.getBody()),
                        SpringMethodType.PUT, bindingIndex);
            case POST:
                return generateMethodCode(httpRule.getPost(), httpRule.getBody().isEmpty() ?
                                Optional.empty() : Optional.of(httpRule.getBody()),
                        SpringMethodType.POST, bindingIndex);
            case DELETE:
                return generateMethodCode(httpRule.getDelete(), httpRule.getBody().isEmpty() ?
                                Optional.empty() : Optional.of(httpRule.getBody()),
                        SpringMethodType.DELETE, bindingIndex);
            case PATCH:
                return generateMethodCode(httpRule.getPatch(), httpRule.getBody().isEmpty() ?
                                Optional.empty() : Optional.of(httpRule.getBody()),
                        SpringMethodType.PATCH, bindingIndex);
            case CUSTOM:
                log.error("Custom HTTP Rule Pattern Not Supported!\n {}",
                        TextFormat.printToString(httpRule.getCustom()));
                return new MethodTemplate(SpringMethodType.POST);
        }
        return new MethodTemplate(SpringMethodType.POST);
    }

    @Nonnull
    private String variableForPath(@Nonnull final String path) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, path.replace(".", "_"));
    }

    @Nonnull
    private String generateVariableSetter(@Nonnull final String path,
                                          @Nonnull final FieldDescriptor field,
                                          final boolean isList) {
        final StringBuilder setFieldBuilder = new StringBuilder();
        final Deque<String> pathStack = new ArrayDeque<>(Arrays.asList(path.split("\\.")));

        if (isList && !isRepeated(field)) {
            setFieldBuilder.append("if (!")
                    .append(variableForPath(path))
                    .append(".isEmpty()) {")
                    .append("inputBuilder.set")
                    .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                    .append("(")
                    .append(variableForPath(path))
                    .append(".get(0)");
            if (isMessageOrEnum(field)) {
                setFieldBuilder.append(".toProto()");
            }
            setFieldBuilder.append(");")
                    .append("}");

        } else {
            setFieldBuilder.append(" inputBuilder");

            while (pathStack.size() > 1) {
                setFieldBuilder.append(".get")
                        .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                        .append("Builder()");
            }

            if (isRepeated(field)) {
                setFieldBuilder.append(".addAll")
                        .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                        .append("(")
                        .append(variableForPath(path))
                        .append(");");
            } else {
                setFieldBuilder.append(".set")
                        .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                        .append("(")
                        .append(variableForPath(path));
                if (isMessageOrEnum(field)) {
                    setFieldBuilder.append(".toProto()");
                }
                setFieldBuilder.append(");");
            }
        }

        setFieldBuilder.append("return inputBuilder;");

        return setFieldBuilder.toString();
    }

    private boolean isMessageOrEnum(@Nonnull FieldDescriptor field) {
        return field.getProto().getType().equals(Type.TYPE_MESSAGE) || field.getProto().getType().equals(Type.TYPE_ENUM);
    }

    private boolean isRepeated(@Nonnull FieldDescriptor field) {
        return field.getProto().getLabel().equals(Label.LABEL_REPEATED);
    }

    private String lowerSnakeToUpperCamel(String value) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, value);
    }


    @Nonnull
    private MethodTemplate generateMethodCode(@Nonnull final String pattern,
                                              @Nonnull final Optional<String> bodyPattern,
                                              @Nonnull final SpringMethodType methodType,
                                              @Nonnull final Optional<Integer> bindingIndex) {
        final PathTemplate template = new PathTemplate(pattern);
        final MessageDescriptor inputDescriptor = serviceMethodDescriptor.getInputMessage();
        final Set<String> boundVariables = template.getBoundVariables();

        final MethodGenerationFieldVisitor fieldVisitor = new MethodGenerationFieldVisitor(boundVariables);

        // We visit all fields. There are two possibilities:
        // 1) Field is "bound" to the path.
        // 2) Field is not bound. It may become a query parameter, or be in the request body.
        inputDescriptor.visitFields(fieldVisitor);

        final List<String> requestToInputSteps = new ArrayList<>();

        if (bodyPattern.isPresent()) {
            final String body = StringUtils.strip(bodyPattern.get());
            if (body.equals("*")) {
                requestToInputSteps.add(".flatMap(inputBuilder -> serverRequest.bodyToMono("
                        + getBodyType(true)
                        + ".class).map(inputDto -> inputBuilder.mergeFrom(inputDto.toProto())))");
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
                    throw new IllegalArgumentException("Invalid body: " + body +
                            ". Body must refer to a non-repeated/map field.");
                }

                requestToInputSteps.add(".flatMap(inputBuilder -> serverRequest.bodyToMono("
                        + bodyField.getType()
                        + ".class).filter(Objects::nonNull).map("
                        + variableForPath(body)
                        + " -> {"
                        + generateVariableSetter(body, bodyField, false)
                        + "}))");
            }
        } else {
            fieldVisitor.getQueryParamFields().forEach((path, type) -> {
                Map<String, Object> context = new HashMap<>();
                context.put("convert", convertString("p", type.getTypeName()));
                context.put("type", type.getTypeName());
                context.put("variable", variableForPath(path));
                context.put("variableSetter", generateVariableSetter(path, type, true));
                requestToInputSteps.add(apply("flatmap_variable_query_parameter", context));
            });
        }

        // Set the path fields after the body, so the path fields override anything set
        // in the body (if there are collisions).
        //
        // @PathVariable(name = <path>) <Type> <camelcasePath>
        fieldVisitor.getPathFields().forEach((path, type) -> {
            Map<String, Object> context = new HashMap<>();
            context.put("convert", convertString("serverRequest.pathVariable(\"" + variableForPath(path) + "\")", type.getType()));
            context.put("type", type.getType());
            context.put("variable", variableForPath(path));
            context.put("variableSetter", generateVariableSetter(path, type, false));
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

        return new MethodTemplate(methodType)
                .setPath(template.getQueryPath())
                .setIsRequestJson(bodyPattern.isPresent())
                .setRequestToInput(requestToInput.toString())
                .setRestMethodName(StringUtils.uncapitalize(serviceMethodDescriptor.getName()) +
                        bindingIndex.map(index -> Integer.toString(index)).orElse(""));
    }

    private class MethodTemplate {

        private static final String PREPARE_INPUT_NAME = "prepareInput";
        private static final String PATH_NAME = "path";
        private static final String REST_METHOD_NAME = "restMethodName";
        private static final String IS_REQUEST_JSON = "isRequestJson";

        private final Map<String, Object> context;


        MethodTemplate(@Nonnull final SpringMethodType springMethodType) {
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
            context.put("methodType", springMethodType.getType());
            context.put("serviceName", serviceDescriptor.getName());
            context.put("isRest", parameters.hasParameter("style")
                    && parameters.getParameterValue("style").equals("rest")
                    && serviceMethodDescriptor.getHttpRule().isPresent());
            // Defaults.
            context.put(IS_REQUEST_JSON, true);
            context.put(PATH_NAME, "/" + serviceDescriptor.getName() + "/" + StringUtils.uncapitalize(serviceMethodDescriptor.getName()));
            context.put(REST_METHOD_NAME, StringUtils.uncapitalize(serviceMethodDescriptor.getName()));
            context.put(PREPARE_INPUT_NAME, "serverRequest.bodyToMono(" + requestBodyType + ".class).map(" + requestBodyType + "::toProto)");
        }

        @Nonnull
        public MethodTemplate setRequestToInput(@Nonnull final String requestToInputCode) {
            this.context.remove(PREPARE_INPUT_NAME);
            this.context.put(PREPARE_INPUT_NAME, requestToInputCode);
            return this;
        }

        @Nonnull
        public MethodTemplate setPath(@Nonnull final String path) {
            this.context.remove(PATH_NAME);
            this.context.put(PATH_NAME, path);
            return this;
        }

        @Nonnull
        public MethodTemplate setIsRequestJson(final boolean isRequestJson) {
            this.context.remove(IS_REQUEST_JSON);
            this.context.put(IS_REQUEST_JSON, isRequestJson);
            return this;
        }

        @Nonnull
        public MethodTemplate setRestMethodName(@Nonnull final String restMethodName) {
            this.context.remove(REST_METHOD_NAME);
            this.context.put(REST_METHOD_NAME, restMethodName);
            return this;
        }

        @Nonnull
        public String renderHandle() {
            context.put("serviceCall", apply("service_call_handle", context));
            return apply("service_method", context);
        }

        @Nonnull
        public String renderProxy() {
            context.put("serviceCall", apply("service_call_proxy", context));
            return apply("service_method", context);
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

    /**
     * Represents the various HTTP method types, and the associated
     * Spring enum.
     */
    enum SpringMethodType {
        POST("HttpMethod.POST"),
        PUT("HttpMethod.PUT"),
        PATCH("HttpMethod.PATCH"),
        GET("HttpMethod.GET"),
        DELETE("HttpMethod.DELETE");

        private final String springMethodType;

        SpringMethodType(final String springMethodType) {
            this.springMethodType = springMethodType;
        }

        @Nonnull
        public String getType() {
            return springMethodType;
        }
    }

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
        }
        throw new IllegalArgumentException("Dose not convert " + value + " to " + type);
    }
}
