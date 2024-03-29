package io.github.protobufx.protoc.gen.spring.generator;

import com.google.api.AnnotationsProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * This is the central class that does the heavy lifting of:
 * 1) Reading and parsing the {@link CodeGeneratorRequest}
 * 2) Generating the code (extensions of this class control how this is actually done).
 * 3) Writing out the {@link CodeGeneratorResponse}.
 * <p>
 * The user of this class should implement the abstract methods and override the necessary
 * code generation methods, and then use {@link ProtocPluginCodeGenerator#generate()} from
 * their plugin's Main class to do the generation.
 */
public abstract class ProtocPluginCodeGenerator {

    private static final Logger log = LogManager.getLogger();

    /**
     * Stores information about processed messages.
     */
    private final Registry registry = new Registry();

    /**
     * Stores build parameters.
     */
    protected final Parameters parameters = new Parameters();

    /**
     * This method should return the name of the plugin.
     * This will appear in the comments of the generated files (so that it's clear which plugin
     * generated them).
     *
     * @return The name of the plugin.
     */
    @Nonnull
    protected abstract String getPluginName();

    /**
     * This method should return the name of the plugin's outer Java class for a particular file,
     * derived from the name of the outer Java class generated by the regular protobuf compiler.
     *
     * All generated classes from a particular file will be inside this outer class. This is
     * identical to the behaviour of the regular protobuf compiler for Java code, where all
     * classes inside a single .proto file are wrapped in a single Java outer class.
     *
     * @param protoJavaClass The Java class generated by the regular protobuf compiler. For example
     *                       if you have a TestDTO.proto file, this will be "TestDTO".
     * @return The Java class name for the file generated by the user's plugin. For example, if
     *         your plugin is "protoc-generate-money", this might be "TestDTOMoney."
     */
    @Nonnull
    protected String generatePluginJavaClass(@Nonnull final String protoJavaClass) {
        return protoJavaClass;
    };

    /**
     * Generate the Java code for a particular {@link EnumDescriptor}.
     *
     * @param enumDescriptor The {@link EnumDescriptor} for an enum defined in the .proto file.
     * @return An {@link Optional} containing a string of Java code generated from the input descriptor.
     *         An empty {@link Optional} if this particular plugin doesn't want to generate anything based
     *         on enums.
     */
    @Nonnull
    protected Optional<GenerateCode> generateEnumCode(@Nonnull final EnumDescriptor enumDescriptor) {
        return Optional.empty();
    }

    /**
     * Generate the Java code for a particular {@link MessageDescriptor}.
     *
     * @param messageDescriptor The {@link MessageDescriptor} for a message defined in the .proto file.
     * @return An {@link Optional} containing a string of Java code generated from the input descriptor.
     *         An empty {@link Optional} if this particular plugin doesn't want to generate anything based
     *         on messages.
     */
    @Nonnull
    protected Optional<GenerateCode> generateMessageCode(@Nonnull final MessageDescriptor messageDescriptor) {
        return Optional.empty();
    }

    /**
     * Generate the Java code for a particular {@link ServiceDescriptor}.
     *
     * @param serviceDescriptor The {@link ServiceDescriptor} for a message defined in the .proto file.
     * @return An {@link Optional} containing a string of Java code generated from the input descriptor.
     *         An empty {@link Optional} if this particular plugin doesn't want to generate anything based
     *         on services.
     */
    @Nonnull
    protected Optional<GenerateCode> generateServiceCode(@Nonnull final ServiceDescriptor serviceDescriptor) {
        return Optional.empty();
    }

    /**
     * Whether or not to skip generating code for a particular file.
     * Some plugins may only want to generate code for specific files (e.g. files with services
     * defined in them). This method provides a way to do that.
     *
     * Skipped files still get processed for types defined in them. The skipping applies only
     * to the code generation step.
     *
     * @param fileDescriptorProto The descriptor of the file.
     * @return True if the plugin does not want to generate any code for this file. False otherwise.
     */
    protected boolean skipFile(@Nonnull final FileDescriptorProto fileDescriptorProto) {
        return false;
    }

    @Nonnull
    protected FileGenerationUnit getFileGenerationUnit() {
        return FileGenerationUnit.SINGLE_FILE;
    }

    @Nonnull
    protected final Optional<GenerateCode> generateCode(@Nonnull final AbstractDescriptor abstractDescriptor) {
        if (abstractDescriptor instanceof EnumDescriptor) {
            return generateEnumCode((EnumDescriptor)abstractDescriptor);
        } else if (abstractDescriptor instanceof MessageDescriptor) {
            return generateMessageCode((MessageDescriptor) abstractDescriptor);
        } else if (abstractDescriptor instanceof ServiceDescriptor) {
            return generateServiceCode((ServiceDescriptor) abstractDescriptor);
        } else {
            throw new IllegalArgumentException("Unsupported abstract descriptor of class " +
                    abstractDescriptor.getClass().getName());
        }
    }

    /**
     * This is the interface into the {@link ProtocPluginCodeGenerator}.
     * Plugin implementations should call this method from their Main class to:
     * 1) Read the {@link CodeGeneratorRequest} from stdin.
     * 2) Generate code and format a {@link CodeGeneratorResponse}.
     * 3) Write the response to stdout.
     *
     * @throws IOException If there is an issue with reading/writing from/to stdin/stdout.
     */
    public final void generate() throws IOException {
        final ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
        extensionRegistry.add(AnnotationsProto.http);

        final CodeGeneratorRequest req =
                CodeGeneratorRequest.parseFrom(new BufferedInputStream(System.in), extensionRegistry);
        parameters.add(req.getParameter());

        // The request presents the proto file descriptors in topological order
        // w.r.t. dependencies - i.e. the dependencies appear before the dependents.
        // This means we can process one file at a time without a separate linking step,
        // as long as we record the processed messages in the registry.
        final CodeGeneratorResponse response = CodeGeneratorResponse.newBuilder()
                .addAllFile(req.getProtoFileList().stream()
                        .flatMap(proto -> generateFile(proto).stream())
                        .collect(toList()))
                .build();

        final BufferedOutputStream outputStream = new BufferedOutputStream(System.out);
        response.writeTo(outputStream);
        outputStream.flush();
    }

    @Nonnull
    private List<File> generateFile(@Nonnull final FileDescriptorProto fileDescriptorProto) {
        log.info("Registering messages in file: {} in package: {}",
                fileDescriptorProto.getName(),
                fileDescriptorProto.getPackage());

        final FileDescriptorProcessingContext context =
                new FileDescriptorProcessingContext(this, registry, fileDescriptorProto);
        context.startEnumList();
        for (int enumIdx = 0; enumIdx < fileDescriptorProto.getEnumTypeCount(); ++enumIdx) {
            context.startListElement(enumIdx);
            final EnumDescriptorProto enumDescriptor = fileDescriptorProto.getEnumType(enumIdx);
            registry.registerEnum(context, enumDescriptor);
            context.endListElement();
        }
        context.endEnumList();

        context.startMessageList();
        for (int msgIdx = 0; msgIdx < fileDescriptorProto.getMessageTypeCount(); ++msgIdx) {
            context.startListElement(msgIdx);
            final DescriptorProto msgDescriptor = fileDescriptorProto.getMessageType(msgIdx);
            registry.registerMessage(context, msgDescriptor);
            context.endListElement();
        }
        context.endMessageList();

        context.startServiceList();
        for (int svcIdx = 0; svcIdx < fileDescriptorProto.getServiceCount(); ++svcIdx) {
            context.startListElement(svcIdx);
            final ServiceDescriptorProto svcDescriptor = fileDescriptorProto.getService(svcIdx);
            registry.registerService(context, svcDescriptor);
            context.endListElement();
        }
        context.endServiceList();

        log.info("Generating messages in file: {} in package: {}",
                fileDescriptorProto.getName(),
                fileDescriptorProto.getPackage());

        if (skipFile(fileDescriptorProto)) {
            return Collections.emptyList();
        } else {
            return context.generateFile();
        }
    }

    public static class GenerateCode {
        String className;
        String code;

        public GenerateCode(String className, String code) {
            this.className = className;
            this.code = code;
        }

        public String getClassName() {
            return className;
        }

        public String getCode() {
            return code;
        }
    }
}
