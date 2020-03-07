package io.protobufx.protoc.gen.spring.generator;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Map;

/**
 * A wrapper around {@link EnumDescriptorProto} with additional information.
 */
@Immutable
public class EnumDescriptor extends AbstractDescriptor {

    /**
     * Comment for the entire enum.
     */
    private final List<String> comment;

    /**
     * The map of (value name) -> (value index) for the values of this enum.
     * For example, in:
     * enum TestEnum {
     *     TEST = 1;
     * }
     * This should be:
     * ("TEST" -> 1)
     */
    private final ImmutableMap<String, Integer> values;

    /**
     * Comments for the individual values, indexed by value name.
     */
    private final ImmutableMap<String, List<String>> valueComments;

    EnumDescriptor(@Nonnull final FileDescriptorProcessingContext context,
                          @Nonnull final EnumDescriptorProto enumDescriptor) {
        super(context, enumDescriptor.getName());

        final ImmutableMap.Builder<String, Integer> valuesBuilder = ImmutableMap.builder();
        enumDescriptor.getValueList().forEach(valueDescriptor -> {
            valuesBuilder.put(valueDescriptor.getName(), valueDescriptor.getNumber());
        });
        values = valuesBuilder.build();

        // Check for comments on the enum.
        comment = context.getCommentAtPath();

        final ImmutableMap.Builder<String, List<String>> valueCommentsBuilder = ImmutableMap.builder();
        // Check for comments on the enum values.
        context.startEnumValueList();
        for (int i = 0; i < enumDescriptor.getValueList().size(); ++i) {
            // Add the index of the value to the path
            context.startListElement(i);

            final String valueName = enumDescriptor.getValue(i).getName();
            valueCommentsBuilder.put(valueName, context.getCommentAtPath());

            // Remove the last element added in the beginning of the loop.
            context.endListElement();
        }
        context.endEnumValueList();
        valueComments = valueCommentsBuilder.build();
    }

    /**
     * Get the comment on this enum.
     *
     * // This is super important :this comment
     * enum TestEnum {
     *    ...
     * }
     *
     * @return The comment.
     */
    @Nonnull
    public List<String> getComment() {
        return comment;
    }

    /**
     * Get the comment on a particular value of the enum.
     *
     * enum TestEnum {
     *     // You want this value :this comment
     *     GOOD = 1;
     *
     *     // You don't want this one :or this one
     *     BAD = 2;
     * }
     *
     * @param valueName The name of the value.
     * @return The comment, or an empty string.
     */
    @Nonnull
    public List<String> getValueComment(@Nonnull String valueName) {
        return valueComments.getOrDefault(valueName, FileDescriptorProcessingContext.EMPTY_COMMENT);
    }

    /**
     * Get the name-value pairs for possible values of the enum.
     *
     * enum TestEnum {
     *     GOOD = 1;
     *     BAD = 2;
     * }
     *
     * Should return {GOOD:1, BAD:2}
     *
     * @return The map from value name to value index.
     */
    @Nonnull
    public Map<String, Integer> getValues() {
        return values;
    }
}
