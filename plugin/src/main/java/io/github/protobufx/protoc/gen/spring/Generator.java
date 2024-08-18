package io.github.protobufx.protoc.gen.spring;


import io.github.protobufx.protoc.gen.spring.generator.FileGenerationUnit;
import io.github.protobufx.protoc.gen.spring.generator.ProtocPluginCodeGenerator;
import io.github.protobufx.protoc.gen.spring.generator.ServiceDescriptor;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;

import static io.github.protobufx.protoc.gen.spring.generator.Template.apply;
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

    @Nonnull
    @Override
    protected FileGenerationUnit getFileGenerationUnit() {
        return FileGenerationUnit.SERVICE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    protected Optional<GenerateCode> generateServiceCode(@Nonnull final ServiceDescriptor serviceDescriptor) {
        final String responseWrapper = serviceDescriptor.getName() + "Response";

        HashMap<String, Object> context = new HashMap<>();
        context.put("pluginName", this.getPluginName());
        context.put("pkgName", serviceDescriptor.getJavaPkgName());
        context.put("protoSourceName", serviceDescriptor.getProtoSourceName());
        context.put("serviceName", serviceDescriptor.getName());
        String outerClassName = serviceDescriptor.getName() + "Rest";
        context.put("outerClassName", outerClassName);
        String serviceClassName = serviceDescriptor.getName() + "Handler";
        context.put("serviceClassName", serviceClassName);
        context.put("serviceGrpcProxyClassName", serviceClassName + "GrpcProxy");
        context.put("responseWrapper", responseWrapper);
        context.put("package", serviceDescriptor.getJavaPkgName());
        context.put("packageProto", serviceDescriptor.getProtoPkgName());
        List<Map<String, Object>> methods = serviceDescriptor.getMethodDescriptors().stream()
                .map(serviceMethodDescriptor ->
                        new MethodGenerator(serviceDescriptor, serviceMethodDescriptor, responseWrapper).getMethodContexts())
                .flatMap(Collection::stream)
                .collect(toList());
        List<Map<String, Object>> routeDefinitions = methods.stream()
                .map(m -> {
                    Map<String, Object> method = new HashMap<>(m);
                    method.put("metaPath", String.valueOf(m.get("path")).replaceAll("\\{.*?}", "{}"));
                    return method;
                })
                .sorted(Comparator.comparing((Function<Map<String, Object>, String>) m -> (String) m.get("metaPath")).reversed())
                .collect(toList());
        context.put("methods", methods);
        context.put("routeDefinitions", routeDefinitions);

        String serviceHandler = apply("service", context);

        return Optional.of(new GenerateCode(outerClassName, serviceHandler));
    }

}
