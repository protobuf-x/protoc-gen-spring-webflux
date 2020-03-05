package io.protobufx.protoc.gen.spring;

import com.google.api.HttpRule;
import com.google.common.base.CaseFormat;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.TextFormat;
import io.protobufx.protoc.gen.spring.generator.*;
import io.protobufx.protoc.gen.spring.generator.ServiceMethodDescriptor.MethodType;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static java.util.stream.Collectors.joining;

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
        if (!serviceMethodDescriptor.getHttpRule().isPresent()) {
            return Collections.emptyList();
        }

        final HttpRule topLevelRule = serviceMethodDescriptor.getHttpRule().get();
        final List<Map<String, Object>> allMethodsContexts = new ArrayList<>();
        getMethodContextFromHttpRule(topLevelRule, null).ifPresent(allMethodsContexts::add);

        // No recursion allowed - additional bindings will not contain rules that contain
        // additional bindings!
        for (int i = 0; i < topLevelRule.getAdditionalBindingsCount(); ++i) {
            getMethodContextFromHttpRule(topLevelRule.getAdditionalBindings(i), i)
                    .ifPresent(allMethodsContexts::add);
        }
        return allMethodsContexts;
    }

    @Nonnull
    private Optional<Map<String, Object>> getMethodContextFromHttpRule(@Nonnull final HttpRule httpRule,
                                                                       @Nullable final Integer bindingIndex) {
        switch (httpRule.getPatternCase()) {
            case GET:
                return Optional.of(getMethodContext(httpRule.getGet(), null,
                        httpRule.getPatternCase(), bindingIndex));
            case PUT:
                return Optional.of(getMethodContext(httpRule.getPut(), httpRule.getBody().isEmpty() ? null : httpRule.getBody(),
                        httpRule.getPatternCase(), bindingIndex));
            case POST:
                return Optional.of(getMethodContext(httpRule.getPost(), httpRule.getBody().isEmpty() ? null : httpRule.getBody(),
                        httpRule.getPatternCase(), bindingIndex));
            case DELETE:
                return Optional.of(getMethodContext(httpRule.getDelete(), httpRule.getBody().isEmpty() ? null : httpRule.getBody(),
                        httpRule.getPatternCase(), bindingIndex));
            case PATCH:
                return Optional.of(getMethodContext(httpRule.getPatch(), httpRule.getBody().isEmpty() ? null : httpRule.getBody(),
                        httpRule.getPatternCase(), bindingIndex));
            case CUSTOM:
                log.error("Custom HTTP Rule Pattern Not Supported!\n {}", TextFormat.printToString(httpRule.getCustom()));
                return Optional.empty();
        }
        return Optional.empty();
    }

    @Nonnull
    private Map<String, Object> getMethodContext(@Nonnull final String pattern,
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

        final Map<String, Object> rootContext = new HashMap<>();
        rootContext.put("requestType", inputDescriptor.getQualifiedOriginalName());

        if (bodyPattern != null) {
            final String body = StringUtils.strip(bodyPattern);
            final Map<String, Object> context = new HashMap<>();
            if (body.equals("*")) {
                context.put("wildcard", true);
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
                context.put("type", bodyField.getContentMessage().get().getQualifiedOriginalName());
                context.put("setterName", setterName(body, false));
            }
            rootContext.put("body", context);
        } else {
            List<Map<String, Object>> parameters = new ArrayList<>();
            fieldVisitor.getQueryParamFields().forEach((path, type) -> {
                Map<String, Object> context = new HashMap<>();
                String typeName = type.getContentMessage()
                        .map(AbstractDescriptor::getQualifiedOriginalName)
                        .orElse(type.getTypeName());
                context.put("convert", convertString("p", typeName));
                context.put("type", typeName);
                context.put("variable", lowerSnakeToLowerCamel(path));
                context.put("isRepeated", isRepeated(type));
                context.put("setterName", setterName(path, isRepeated(type)));
                parameters.add(context);
            });
            rootContext.put("parameters", parameters);
        }

        List<Map<String, Object>> paths = new ArrayList<>();
        fieldVisitor.getPathFields().forEach((path, type) -> {
            Map<String, Object> context = new HashMap<>();
            context.put("convert", convertString("p", type.getType()));
            context.put("type", type.getType());
            context.put("variable", lowerSnakeToLowerCamel(path));
            context.put("setterName", setterName(path, false));
            paths.add(context);
        });
        rootContext.put("paths", paths);

        String index = bindingIndex == null ? "" : Integer.toString(bindingIndex);
        String restMethodName = StringUtils.uncapitalize(serviceMethodDescriptor.getName()) + index;
        return getContext(httpMethod,
                template.getQueryPath(),
                bodyPattern != null,
                rootContext,
                restMethodName,
                lowerCamelToUpperSnake(restMethodName) + "_PATH");
    }


    Map<String, Object> getContext(@Nonnull final HttpRule.PatternCase httpMethod,
                                   @Nonnull final String path,
                                   final boolean isRequestJson,
                                   @Nonnull final Map<String, Object> requestContext,
                                   @Nonnull final String restMethodName,
                                   @Nonnull final String restPathField) {
        final MessageDescriptor inputDescriptor = serviceMethodDescriptor.getInputMessage();
        final MessageDescriptor outputDescriptor = serviceMethodDescriptor.getOutputMessage();
        final MethodType type = serviceMethodDescriptor.getType();
        final Map<String, Object> context = new HashMap<>();
        context.put("resultProto", outputDescriptor.getQualifiedOriginalName());
        context.put("responseWrapper", responseWrapper);
        context.put("requestProto", inputDescriptor.getQualifiedOriginalName());
        context.put("isClientStream", type == MethodType.BI_STREAM || type == MethodType.CLIENT_STREAM);
        context.put("isSingleResponse", type == MethodType.SIMPLE || type == MethodType.CLIENT_STREAM);
        context.put("comments", serviceMethodDescriptor.getComment());
        context.put("methodName", StringUtils.uncapitalize(serviceMethodDescriptor.getName()));
        context.put("methodProto", serviceMethodDescriptor.getName());
        context.put("methodTypeName", httpMethod.name());
        context.put("serviceName", serviceDescriptor.getName());
        context.put("requestContext", requestContext);
        context.put("path", path);
        context.put("isRequestJson", isRequestJson);
        context.put("restMethodName", restMethodName);
        context.put("restPathField", restPathField);
        return context;
    }


    @Nonnull
    private String setterName(@Nonnull final String path, boolean isRepeated) {
        final StringBuilder setFieldBuilder = new StringBuilder();
        final Deque<String> pathStack = new ArrayDeque<>(Arrays.asList(path.split("\\.")));

        while (pathStack.size() > 1) {
            setFieldBuilder.append(".get")
                    .append(lowerSnakeToUpperCamel(pathStack.removeFirst()))
                    .append("Builder()");
        }

        setFieldBuilder.append(isRepeated ? ".addAll" : ".set")
                .append(lowerSnakeToUpperCamel(pathStack.removeFirst()));

        return setFieldBuilder.toString();
    }

    private boolean isRepeated(@Nonnull FieldDescriptor field) {
        return field.getProto().getLabel().equals(Label.LABEL_REPEATED);
    }

    @Nonnull
    private String lowerSnakeToLowerCamel(@Nonnull final String path) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, path.replace(".", "_"));
    }

    @Nonnull
    private String lowerSnakeToUpperCamel(@Nonnull final String value) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, value);
    }

    @Nonnull
    private String lowerCamelToUpperSnake(@Nonnull final String value) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, value);
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
