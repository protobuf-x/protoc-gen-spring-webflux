package io.disc99.protoc.gen.spring;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import io.disc99.protoc.gen.spring.generator.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * An implementation of {@link ProtocPluginCodeGenerator} that generates Spring Framework-compatible
 * WebFlux handlers and swagger-annotated POJOs for gRPC services.
 */
@Slf4j
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
        return template("imports").text();
    }

    @SneakyThrows // TODO
    Template template(String file) {
        TemplateLoader loader = new ClassPathTemplateLoader();
        Handlebars handlebars = new Handlebars(loader).prettyPrint(true).with(EscapingStrategy.NOOP);
        return handlebars.compile(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    @SneakyThrows // TODO
    protected Optional<String> generateServiceCode(@Nonnull final ServiceDescriptor serviceDescriptor) {
        final String responseWrapper = serviceDescriptor.getName() + "Response";

        HashMap<String, Object> context = new HashMap<>();
        context.put("serviceName", serviceDescriptor.getName());
        context.put("responseWrapper", responseWrapper);
        context.put("package", serviceDescriptor.getJavaPkgName());
        context.put("methods", serviceDescriptor.getMethodDescriptors().stream()
                .map(this::methodContext)
                .collect(Collectors.toList()));
        return Optional.of(template("service").apply(context));
    }

    Map<String, Object> methodContext(@Nonnull final ServiceMethodDescriptor serviceMethodDescriptor) {
        final MessageDescriptor inputDescriptor = serviceMethodDescriptor.getInputMessage();
        final MessageDescriptor outputDescriptor = serviceMethodDescriptor.getOutputMessage();
        Map<String, Object> context = new HashMap<>();
        context.put("resultProto", outputDescriptor.getQualifiedOriginalName());
        context.put("requestProto", inputDescriptor.getQualifiedOriginalName());
        context.put("methodName", StringUtils.uncapitalize(serviceMethodDescriptor.getName()));
        context.put("methodProto", serviceMethodDescriptor.getName());
        return context;
    }

}
