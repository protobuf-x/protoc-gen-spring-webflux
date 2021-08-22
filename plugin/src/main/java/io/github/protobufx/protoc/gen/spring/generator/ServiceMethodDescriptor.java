package io.github.protobufx.protoc.gen.spring.generator;

import com.google.api.AnnotationsProto;
import com.google.api.HttpRule;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Optional;

/**
 * A wrapper around {@link MethodDescriptorProto}, which describes a single rpc method in
 * a service.
 */
@Immutable
public class ServiceMethodDescriptor {

    /**
     * The comment on the method.
     */
    private final List<String> comment;

    /**
     * The {@link MessageDescriptor} for the input to the method.
     */
    private final MessageDescriptor inputMessage;

    /**
     * The {@link MessageDescriptor} for the output from the method.
     */
    private final MessageDescriptor outputMessage;

    private final MethodType type;

    private final MethodDescriptorProto methodDescriptorProto;

    ServiceMethodDescriptor(@Nonnull final FileDescriptorProcessingContext context,
                            @Nonnull final MethodDescriptorProto methodDescriptorProto) {
        final Registry registry = context.getRegistry();
        this.comment = context.getCommentAtPath();
        // These casts are totally safe, because the input type is supposed to
        // be a message.
        this.inputMessage = (MessageDescriptor)registry.getMessageDescriptor(
                methodDescriptorProto.getInputType());
        this.outputMessage = (MessageDescriptor)registry.getMessageDescriptor(
                methodDescriptorProto.getOutputType());
        this.type = MethodType.fromDescriptor(methodDescriptorProto);
        this.methodDescriptorProto = methodDescriptorProto;
    }

    @Nonnull
    public List<String> getComment() {
        return comment;
    }

    @Nonnull
    public String getName() {
        return methodDescriptorProto.getName();
    }

    @Nonnull
    public MethodType getType() {
        return type;
    }

    @Nonnull
    public MethodDescriptorProto getProto() {
        return methodDescriptorProto;
    }

    @Nonnull
    public MessageDescriptor getInputMessage() {
        return inputMessage;
    }

    @Nonnull
    public MessageDescriptor getOutputMessage() {
        return outputMessage;
    }

    @Nonnull
    public Optional<HttpRule> getHttpRule() {
        final DescriptorProtos.MethodOptions messageOptions = methodDescriptorProto.getOptions();
        if (messageOptions.hasExtension(AnnotationsProto.http)) {
            return Optional.of(messageOptions.getExtension(AnnotationsProto.http));
        } else {
            return Optional.empty();
        }
    }

    /**
     * A helper enum to differentiate the types of methods
     * based on the use of client or server side streaming.
     */
    public enum MethodType {
        SIMPLE,
        SERVER_STREAM,
        CLIENT_STREAM,
        BI_STREAM;

        public static MethodType fromDescriptor(@Nonnull final MethodDescriptorProto methodDescriptor) {
            if (methodDescriptor.getServerStreaming() && !methodDescriptor.getClientStreaming()) {
                return MethodType.SERVER_STREAM;
            } else if (methodDescriptor.getClientStreaming() && !methodDescriptor.getServerStreaming()) {
                return MethodType.CLIENT_STREAM;
            } else if (methodDescriptor.getServerStreaming() && methodDescriptor.getClientStreaming()) {
                return MethodType.BI_STREAM;
            } else {
                return MethodType.SIMPLE;
            }
        }
    }
}
