package io.protobufx.protoc.gen.spring;


import io.protobufx.protoc.gen.spring.generator.*;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;

import static io.protobufx.protoc.gen.spring.generator.Template.apply;
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
        context.put("imports", this.generateImports());
        context.put("pkgName", serviceDescriptor.getJavaPkgName());
        context.put("protoSourceName", serviceDescriptor.getProtoSourceName());
        context.put("serviceName", serviceDescriptor.getName());
        String serviceClassName = serviceDescriptor.getName() + "Handler";
        context.put("serviceClassName", serviceClassName);
        context.put("responseWrapper", responseWrapper);
        context.put("package", serviceDescriptor.getJavaPkgName());
        context.put("packageProto", serviceDescriptor.getProtoPkgName());
        context.put("methods", serviceDescriptor.getMethodDescriptors().stream()
                .map(serviceMethodDescriptor ->
                        new MethodGenerator(serviceDescriptor, serviceMethodDescriptor, responseWrapper).getMethodContexts())
                .flatMap(Collection::stream)
                .collect(toList()));

        String serviceHandler = apply("service_handler", context);

        return Optional.of(new GenerateCode(serviceClassName, serviceHandler));
    }
}
