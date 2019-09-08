package io.disc99.protoc.gen.spring;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.google.api.HttpRule;
import com.google.common.base.CaseFormat;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.TextFormat;
import io.disc99.protoc.gen.spring.generator.*;
import io.disc99.protoc.gen.spring.generator.ServiceMethodDescriptor.MethodType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A utility class to encapsulate the generation of HTTP methods for gRPC services.
 */
@Slf4j
public class MethodGenerator {

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
    public String generateCode() {
        if (!serviceMethodDescriptor.getHttpRule().isPresent()) {
            return new MethodTemplate(SpringMethodType.POST).render();
        }

        final HttpRule topLevelRule = serviceMethodDescriptor.getHttpRule().get();
        final StringBuilder allMethodsCode = new StringBuilder();
        allMethodsCode.append(generateMethodFromHttpRule(topLevelRule, Optional.empty()).render());

        // No recursion allowed - additional bindings will not contain rules that contain
        // additional bindings!
        for (int i = 0; i < topLevelRule.getAdditionalBindingsCount(); ++i) {
            allMethodsCode.append(generateMethodFromHttpRule(
                    topLevelRule.getAdditionalBindings(i), Optional.of(i)).render());
        }
        return allMethodsCode.toString();
    }

    @Nonnull
    public List<Map<String, Object>> getMethodContexts() {
        if (!serviceMethodDescriptor.getHttpRule().isPresent()) {
            return Collections.singletonList(new MethodTemplate(SpringMethodType.POST).getContext());
        }

        final HttpRule topLevelRule = serviceMethodDescriptor.getHttpRule().get();
        final List<Map<String, Object>> allMethodsContexts = new ArrayList<>();
        allMethodsContexts.add(generateMethodFromHttpRule(topLevelRule, Optional.empty()).getContext());

        // No recursion allowed - additional bindings will not contain rules that contain
        // additional bindings!
        for (int i = 0; i < topLevelRule.getAdditionalBindingsCount(); ++i) {
            allMethodsContexts.add(
                    generateMethodFromHttpRule(topLevelRule.getAdditionalBindings(i), Optional.of(i)).getContext());
        }
        return allMethodsContexts;
    }

    @Nonnull
    private MethodTemplate generateMethodFromHttpRule(@Nonnull final HttpRule httpRule,
                                              @Nonnull final Optional<Integer> bindingIndex) {
//        if (clientStream()) {
//            log.warn("HTTP Rule Patterns not supported for client-stream method: " +
//                    serviceMethodDescriptor.getName() + "Generating default POST method.");
//            return new MethodTemplate(SpringMethodType.POST).render();
//        }
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
                                          @Nonnull final FieldDescriptor field) {
        final StringBuilder setFieldBuilder = new StringBuilder();
        setFieldBuilder.append(" inputBuilder");

        final Deque<String> pathStack = new ArrayDeque<>(Arrays.asList(path.split("\\.")));
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

        String requestBody = "";
//        final List<String> requestArgs = new ArrayList<>();
        final List<String> requestToInputSteps = new ArrayList<>();

//        final String inputBuilderPrototype;
        if (bodyPattern.isPresent()) {
            final String body = StringUtils.strip(bodyPattern.get());
            if (body.equals("*")) {
//                requestArgs.add("@RequestBody " + getBodyType(true) + " inputDto");
//                requestBody = getBodyType(true) + " inputDto = serverRequest.bodyToMono(" + getBodyType(true) + ".class).block();";
//                inputBuilderPrototype = "inputDto.toProto()";
                requestToInputSteps.add(".flatMap(inputBuilder -> serverRequest.bodyToMono("
                        + getBodyType(true)
                        + ".class).map(inputDto -> inputBuilder.mergeFrom(inputDto.toProto())))");
            } else {
//                inputBuilderPrototype = "";
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
                                .collect(Collectors.joining(", "))));
                if (bodyField.isList() || bodyField.isMapField()) {
                    throw new IllegalArgumentException("Invalid body: " + body +
                            ". Body must refer to a non-repeated/map field.");
                }
//                requestArgs.add("@RequestBody(required = " + bodyField.isRequired() + ") " + bodyField.getType() + " " + variableForPath(body));
//                requestBody = bodyField.getType() + " " + variableForPath(body) + " = serverRequest.bodyToMono(" + bodyField.getType() + ".class).block();";

//                requestToInputSteps.add(generateVariableSetter(body, bodyField));

                requestToInputSteps.add(".flatMap(inputBuilder -> serverRequest.bodyToMono("
                        + bodyField.getType()
                        + ".class).filter(Objects::nonNull).map("
                        + variableForPath(body)
                        + " -> {"
                        + generateVariableSetter(body, bodyField)
                        + "}))");
            }
        } else {
//            inputBuilderPrototype = "";
            // @RequestParam(name = <path>) <Type> <camelcasePath>
            fieldVisitor.getQueryParamFields().forEach((path, type) -> {
//                requestArgs.add("@RequestParam(name = \"" + path + "\", required = " + type.getProto().getLabel().equals(Label.LABEL_REQUIRED) + ") " + type.getType() + " " + variableForPath(path));
//                requestArgs.add(type.getType() + " " + variableForPath(path) + " = " + "serverRequest.queryParam(\"" + path + "\").map(p -> Arrays.asList(" + convertString("p", type.getTypeName()) + ")).orElse(null);");
//                requestToInputSteps.add(generateVariableSetter(path, type));

                requestToInputSteps.add(".flatMap(inputBuilder -> Mono.just(serverRequest.queryParam(\"" + path + "\").map(p -> Arrays.asList(" + convertString("p", type.getTypeName()) + ")).orElse(null))"
                        + ".filter(Objects::nonNull).map("
                        + variableForPath(path)
                        + " -> {"
                        + generateVariableSetter(path, type)
                        + "}))");

            });
        }

        // Set the path fields after the body, so the path fields override anything set
        // in the body (if there are collisions).
        //
        // @PathVariable(name = <path>) <Type> <camelcasePath>
        fieldVisitor.getPathFields().forEach((path, type) -> {
//            requestArgs.add("@PathVariable(name = \"" + path + "\") " + type.getType() + " " + variableForPath(path));
//            requestArgs.add(type.getType() + " " + variableForPath(path) + " = " + convertString("serverRequest.pathVariable(\"" + path + "\")", type.getType()) + ";");
//            requestToInputSteps.add(generateVariableSetter(path, type));

            requestToInputSteps.add(".flatMap(inputBuilder -> Mono.just(" + convertString("serverRequest.pathVariable(\"" + path + "\")", type.getType()) + ")"
                    + ".filter(Objects::nonNull).map("
                    + variableForPath(path)
                    + " -> {"
                    + generateVariableSetter(path, type)
                    + "}))");

        });

//        final StringBuilder requestToInput = new StringBuilder(inputDescriptor.getQualifiedOriginalName())
//                .append(".Builder inputBuilder = ")
//                .append(inputDescriptor.getQualifiedOriginalName())
//                .append(".newBuilder(").append(inputBuilderPrototype).append(");");
        final StringBuilder requestToInput = new StringBuilder()
                .append("Mono.just(")
                .append(inputDescriptor.getQualifiedOriginalName())
                .append(".newBuilder())");

        requestToInputSteps.forEach(requestToInput::append);

//        requestToInput.append("input = inputBuilder.build();");
        requestToInput.append(".map(")
                .append(inputDescriptor.getQualifiedOriginalName())
                .append(".Builder::build)");

        return new MethodTemplate(methodType)
                .setPath(template.getQueryPath())
                .setIsRequestJson(bodyPattern.isPresent())
//                .setRequestBody(requestBody)
//                .setRequestArgs(String.join("\n", requestArgs))
                .setRequestToInput(requestToInput.toString())
                .setRestMethodName(serviceMethodDescriptor.getName() +
                        bindingIndex.map(index -> Integer.toString(index)).orElse(""));
    }

    private class MethodTemplate {

//        private static final String REQUEST_ARGS_NAME = "requestArgs";
//        private static final String REQUEST_BODY_NAME = "requestBody";
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
//            context.put(REQUEST_ARGS_NAME, "@RequestBody " + requestBodyType + " inputDto");
//            context.put(REQUEST_BODY_NAME, requestBodyType + " inputDto = serverRequest.bodyToMono(" + requestBodyType + ".class).block();");
            context.put(PATH_NAME, "/" + serviceDescriptor.getName() + "/" + StringUtils.uncapitalize(serviceMethodDescriptor.getName()));
            context.put(REST_METHOD_NAME, StringUtils.uncapitalize(serviceMethodDescriptor.getName()));
            final String defaultPrepareInput;
//            if (clientStream()) {
//                defaultPrepareInput = "input = inputDto.stream()" +
//                            ".map(dto -> dto.toProto())" +
//                            ".collect(Collectors.toList());";
//            } else {
//                defaultPrepareInput = "input = inputDto.toProto();";
                defaultPrepareInput = "serverRequest.bodyToMono(" + requestBodyType + ".class).map(" + requestBodyType + "::toProto)";
//            }

            context.put(PREPARE_INPUT_NAME, defaultPrepareInput);
        }

//        @Nonnull
//        public MethodTemplate setRequestBody(@Nonnull final String requestBodyCode) {
//            this.context.remove(REQUEST_BODY_NAME);
//            this.context.put(REQUEST_BODY_NAME, requestBodyCode);
//            return this;
//        }

//        @Nonnull
//        public MethodTemplate setRequestArgs(@Nonnull final String requestArgsCode) {
//            this.context.remove(REQUEST_ARGS_NAME);
//            this.context.put(REQUEST_ARGS_NAME, requestArgsCode);
//            return this;
//        }

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
        @SneakyThrows // TODO
        public String render() {
            TemplateLoader loader = new ClassPathTemplateLoader();
            Handlebars handlebars = new Handlebars(loader).prettyPrint(true).with(EscapingStrategy.NOOP);

            Template template = handlebars.compile("service_method");
            return template.apply(context);
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
//        final boolean stream = request ? clientStream() : serverStream();

        String requestBodyType = descriptor.getQualifiedName();
//        if (stream) {
//            requestBodyType = "List<" + requestBodyType + ">";
//        }
        return requestBodyType;
    }

    private String getProtoInputType() {
        final MessageDescriptor descriptor = serviceMethodDescriptor.getInputMessage();
        String requestBodyType = descriptor.getQualifiedOriginalName();
//        if (clientStream()) {
//            requestBodyType = "List<" + requestBodyType + ">";
//        }
        return requestBodyType;
    }

//    private boolean clientStream() {
//        return serviceMethodDescriptor.getType() == MethodType.CLIENT_STREAM ||
//            serviceMethodDescriptor.getType() == MethodType.BI_STREAM;
//    }
//
//    private boolean serverStream() {
//        return serviceMethodDescriptor.getType() == MethodType.SERVER_STREAM ||
//                serviceMethodDescriptor.getType() == MethodType.BI_STREAM;
//    }

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
