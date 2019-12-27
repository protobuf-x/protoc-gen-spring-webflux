package io.protobufx.protoc.gen.spring.generator;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;


/**
 * The registry keeps track of already-processed descriptors.
 * Since there are links between the different descriptors (when they
 * reference other messages and/or packages) we need a central place to
 * index descriptor information. This is that place.
 */
class Registry {

    private Map<String, AbstractDescriptor> messageDescriptorMap = new HashMap<>();

    @Nonnull
    ServiceDescriptor registerService(final FileDescriptorProcessingContext context,
                                      final ServiceDescriptorProto serviceDescriptor) {
        final ServiceDescriptor descriptor = new ServiceDescriptor(context, serviceDescriptor);
        messageDescriptorMap.put(descriptor.getQualifiedProtoName(), descriptor);
        messageDescriptorMap.put(descriptor.getNameWithinOuterClass(), descriptor);
        return descriptor;
    }

    @Nonnull
    EnumDescriptor registerEnum(final FileDescriptorProcessingContext context,
                                final EnumDescriptorProto enumDescriptor) {
        final EnumDescriptor descriptor = new EnumDescriptor(context, enumDescriptor);
        messageDescriptorMap.put(descriptor.getQualifiedProtoName(), descriptor);
        messageDescriptorMap.put(descriptor.getNameWithinOuterClass(), descriptor);
        return descriptor;
    }


    @Nonnull
    MessageDescriptor registerMessage(final FileDescriptorProcessingContext context,
                                      final DescriptorProto descriptorProto) {
        final ImmutableList.Builder<AbstractDescriptor> childrenBuilder = new ImmutableList.Builder<>();

        // Processing the messages, need to include that in the path.
        context.startNestedMessageList(descriptorProto.getName());
        for (int i = 0; i < descriptorProto.getNestedTypeCount(); ++i) {
            context.startListElement(i);
            final DescriptorProto nestedDescriptor = descriptorProto.getNestedType(i);
            final MessageDescriptor descriptor = registerMessage(
                    context,
                    nestedDescriptor);
            childrenBuilder.add(descriptor);

            context.endListElement();
        }
        // Done processing messages.
        context.endNestedMessageList();

        // Process nested enums.
        context.startNestedEnumList(descriptorProto.getName());
        for (int nestedEnumIdx = 0; nestedEnumIdx < descriptorProto.getEnumTypeCount(); ++nestedEnumIdx) {
            context.startListElement(nestedEnumIdx);
            final EnumDescriptorProto nestedEnum = descriptorProto.getEnumType(nestedEnumIdx);

            final EnumDescriptor descriptor = registerEnum(context, nestedEnum);
            childrenBuilder.add(descriptor);

            context.endListElement();
        }
        // Done processing nested enums.
        context.endNestedEnumList();

        MessageDescriptor typeDescriptor = new MessageDescriptor(context, descriptorProto, childrenBuilder.build());

        messageDescriptorMap.put(typeDescriptor.getQualifiedProtoName(), typeDescriptor);
        messageDescriptorMap.put(typeDescriptor.getNameWithinOuterClass(), typeDescriptor);
        return typeDescriptor;
    }

    /**
     * Gets a message descriptor in the registry by name.
     * Since we should never try to retrieve descriptors that we haven't processed
     * this method throws a runtime exception if the descriptor is not registered.
     *
     * @param name The name of the descriptor. This can be the short name
     *             (e.g. TestMessage) or the fully qualified name prefixed with
     *             a "." (e.g. .testPkg.TestMessage).
     * @return The descriptor associated with the name.
     * @throws IllegalStateException If the descriptor is not present in the registry.
     */
    @Nonnull
    AbstractDescriptor getMessageDescriptor(@Nonnull final String name) {
        AbstractDescriptor result = null;
        // The protobuf compiler presents
        // fully qualified names prefixed with a ".".
        if (name.startsWith(".")) {
            result = messageDescriptorMap.get(StringUtils.removeStart(name, "."));
        } else {
            result = messageDescriptorMap.get(name);
        }

        if (result == null) {
            throw new IllegalStateException("Message descriptor " + name
                    + " is not present in the registry.");
        }
        return result;
    }

}
