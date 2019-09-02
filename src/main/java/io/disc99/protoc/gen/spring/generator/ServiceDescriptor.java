package io.disc99.protoc.gen.spring.generator;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.util.Collection;

@Immutable
public class ServiceDescriptor extends AbstractDescriptor {
    /**
     * Method name -> descriptor for that method.
     */
    private final ImmutableMap<String, ServiceMethodDescriptor> methodDescriptors;

    private final ServiceDescriptorProto serviceDescriptor;

    public ServiceDescriptor(@Nonnull final FileDescriptorProcessingContext context,
                             @Nonnull final ServiceDescriptorProto serviceDescriptor) {
        super(context, serviceDescriptor.getName());
        this.serviceDescriptor = serviceDescriptor;

        final ImmutableMap.Builder<String, ServiceMethodDescriptor> descriptorsBuilder =
                ImmutableMap.builder();
        context.startServiceMethodList();
        for (int methodIdx = 0; methodIdx < serviceDescriptor.getMethodCount(); ++methodIdx) {
            context.startListElement(methodIdx);
            final MethodDescriptorProto methodDescriptor =
                    serviceDescriptor.getMethod(methodIdx);
            descriptorsBuilder.put(methodDescriptor.getName(),
                    new ServiceMethodDescriptor(context, methodDescriptor));
            context.endListElement();
        }
        context.endServiceMethodList();
        methodDescriptors = descriptorsBuilder.build();
    }

    @Nonnull
    public ServiceDescriptorProto getProto() {
        return serviceDescriptor;
    }

    @Nonnull
    public Collection<ServiceMethodDescriptor> getMethodDescriptors() {
        return methodDescriptors.values();
    }

}
