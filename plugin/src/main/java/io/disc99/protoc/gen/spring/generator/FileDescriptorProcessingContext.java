package io.disc99.protoc.gen.spring.generator;

import com.google.common.base.CaseFormat;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static io.disc99.protoc.gen.spring.generator.Template.apply;
import static java.util.stream.Collectors.toList;

/**
 * This object keeps common information and state about
 * the traversal of a single {@link FileDescriptorProto}.
 */
class FileDescriptorProcessingContext {

    private static final Logger log = LogManager.getLogger();

    static final String EMPTY_COMMENT = "\"\"";

    /**
     * The value for "syntax" that indicates proto3 syntax.
     * See google/protobuf/descriptor.proto
     */
    static final String PROTO_3_SYNTAX = "proto3";

    // START - Common, immutable information
    private final Registry registry;


    /**
     * The protobuf package for the {@link FileDescriptorProto}.
     * For example, if we have a file Test.proto:
     * <p>
     * syntax = "proto2";
     * package testPkg; <-- This is the protoPkg
     * <p>
     * option java_package = "com.vmturbo.testPkg";
     */
    private final String protoPkg;

    /**
     * The Java package for the {@link FileDescriptorProto}.
     * For example, if we have a file Test.proto:
     * <p>
     * syntax = "proto2";
     * package testPkg;
     * <p>
     * option java_package = "com.vmturbo.testPkg"; <-- This is the javaPkg
     * <p>
     * If we there is no java_package option in the proto file
     * then this is equivalent to {@link FileDescriptorProcessingContext#protoPkg}.
     */
    private final String javaPkg;

    /**
     * The outer class to use for all generated code.
     */
    private final OuterClass outerClass;

    /**
     * The comments extracted from the {@link FileDescriptorProto}, indexed by path.
     */
    private final Map<List<Integer>, String> commentsByPath;

    /**
     * The {@link FileDescriptorProto} this context applies to.
     */
    private final FileDescriptorProto fileDescriptorProto;

    /**
     * Whether this file uses proto3 syntax.
     */
    private final boolean proto3Syntax;

    // END - Common, immutable information

    // START - State during traversal.

    /**
     * The path to the current location in the file traversal.
     * The user of the {@link FileDescriptorProcessingContext} is
     * responsible for managing this path via the various
     * path-related methods (e.g. {@link FileDescriptorProcessingContext#startMessageList()}).
     */
    private final LinkedList<Integer> curPath = new LinkedList<>();

    /**
     * Non-empty in nested messages. This is the list of outer messages.
     */
    private final LinkedList<String> outers = new LinkedList<>();

    // END - State during traversal.

    private final ProtocPluginCodeGenerator generator;

    public FileDescriptorProcessingContext(@Nonnull final ProtocPluginCodeGenerator generator,
                                           @Nonnull final Registry registry,
                                           @Nonnull final FileDescriptorProto fileDescriptorProto) {
        this.generator = generator;
        this.fileDescriptorProto = fileDescriptorProto;
        this.registry = registry;
        this.javaPkg = getPackage(fileDescriptorProto);
        this.protoPkg = fileDescriptorProto.getPackage();
        this.outerClass = new OuterClass(generator, fileDescriptorProto);
        // Anything other than the proto3 syntax is treated as not-proto-3.
        this.proto3Syntax = fileDescriptorProto.getSyntax().equals(PROTO_3_SYNTAX);
        // Create map of comments in this file. Need this to annotate
        // da fields with da comments.
        this.commentsByPath = Collections.unmodifiableMap(
                fileDescriptorProto.getSourceCodeInfo().getLocationList().stream()
                        // Ignoring detached comments, and locations without comments.
                        .filter(location -> location.hasTrailingComments() || location.hasLeadingComments())
                        .collect(Collectors.toMap(
                                Location::getPathList,
                                this::getComment,
                                (comment1, comment2) -> {
                                    log.warn("Discarding comment due to duplicate path: {}",
                                            comment2);
                                    return comment1;
                                })));
    }


    @Nonnull
    public String getCommentAtPath() {
        return formatComment(commentsByPath.get(curPath));
    }

    @Nonnull
    public List<String> getOuters() {
        return outers;
    }

    @Nonnull
    public File generateFile() {
        HashMap<String, Object> context = new HashMap<>();
        context.put("pluginName", generator.getPluginName());
        context.put("imports", generator.generateImports());
        context.put("protoSourceName", fileDescriptorProto.getName());
        context.put("pkgName", javaPkg);
        context.put("outerClassName", outerClass.getPluginJavaClass());
        context.put("messageCode", fileDescriptorProto.getMessageTypeList().stream()
                .map(message -> registry.getMessageDescriptor(message.getName()))
                .map(generator::generateCode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList()));
        context.put("enumCode", fileDescriptorProto.getEnumTypeList().stream()
                .map(message -> registry.getMessageDescriptor(message.getName()))
                .map(generator::generateCode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList()));
        context.put("serviceCode", fileDescriptorProto.getServiceList().stream()
                .map(message -> registry.getMessageDescriptor(message.getName()))
                .map(generator::generateCode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList()));

        final String generatedFile = apply("file", context);

        // Run the formatter to pretty-print the code.
        log.info("Running formatter...");

        try {
            final String formattedContent = new Formatter().formatSource(generatedFile);
            return File.newBuilder()
                    .setName(javaPkg.replace('.', '/') + "/" + outerClass.getPluginJavaClass() + ".java")
//                    .setName("./" + outerClass.getPluginJavaClass() + ".java")
                    .setContent(formattedContent)
                    .build();
        } catch (FormatterException e) {
            throw new RuntimeException("Got error " + e.getMessage() + " when formatting content:\n" + generatedFile, e);
        }

    }

    @Nonnull
    public Registry getRegistry() {
        return registry;
    }

    @Nonnull
    public String getJavaPackage() {
        return javaPkg;
    }

    @Nonnull
    public String getProtobufPackage() {
        return protoPkg;
    }

    @Nonnull
    public OuterClass getOuterClass() {
        return outerClass;
    }

    public boolean isProto3Syntax() {
        return proto3Syntax;
    }

    /**
     * Formats a comment string into a string that can be put into the generated code and
     * will look reasonable in swagger documentation.
     * <p>
     * For example:
     * comment saying "stuff" and \n \n line2
     * Becomes:
     * "comment saying \"stuff\" and\n" + "line2"
     *
     * @param comment The input comment, as given in the {@link SourceCodeInfo}.
     * @return The comment to put into the generated code template.
     */
    @Nonnull
    private static String formatComment(@Nullable String comment) {
        if (comment == null) {
            return EMPTY_COMMENT;
        }
        // START WITH: <spaces> comment saying "stuff" and \n \n line2
        // BECOMES: comment saying \"stuff\" and \n \n line2
        String replacedQuotes = StringUtils.strip(comment).replace("\"", "\\\"");
        // BECOMES: "comment saying \"stuff\" and \n \n line2"
        String inQuotes = "\"" + replacedQuotes + "\"";
        // Becomes: {"comment saying \"stuff\" and, line2"}
        List<String> lines = new ArrayList<>();
        for (final String line : inQuotes.split("\n")) {
            final String stripped = StringUtils.strip(line);
            if (stripped.length() > 0) {
                lines.add(stripped);
            }
        }
        // BECOMES: "comment saying \"stuff\" and\n" + "line2"
        return StringUtils.join(lines, "\\n\" + \"");
    }

    /**
     * Get a single comment string representing all the
     * comments in a {@link Location}.
     *
     * @param location The location to examine.
     * @return A string representing the comments at the location.
     */
    private String getComment(Location location) {
        final StringBuilder totalCommentBuilder = new StringBuilder();
        if (location.hasLeadingComments()) {
            totalCommentBuilder.append(location.getLeadingComments());
            // Don't need to worry about manually adding newlines, because trailing newlines are included by default.
        }
        if (location.hasTrailingComments()) {
            totalCommentBuilder.append(location.getTrailingComments());
        }

        return totalCommentBuilder.toString();
    }

    /**
     * Get the package to generate code into.
     *
     * @param fileDescriptorProto The file descriptor we're processing.
     * @return The package to place our generated code.
     */
    private String getPackage(FileDescriptorProto fileDescriptorProto) {
        return fileDescriptorProto.getOptions().hasJavaPackage() ?
                fileDescriptorProto.getOptions().getJavaPackage() : fileDescriptorProto.getPackage();
    }

    // START - Path-related methods.
    // These methods are intended to be used during traversal
    // of the FileDescriptorProto to keep track of the current
    // place in the traversal. We need this place to get
    // the comments at the location, since the comments
    // are stored separately, indexed by the path.

    public void startFieldList() {
        addToPath(DescriptorProto.FIELD_FIELD_NUMBER);
    }

    public void endFieldList() {
        removeFromPath();
    }

    public void startEnumList() {
        addToPath(FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER);
    }

    public void endEnumList() {
        removeFromPath();
    }

    public void startEnumValueList() {
        addToPath(EnumDescriptorProto.VALUE_FIELD_NUMBER);
    }

    public void endEnumValueList() {
        removeFromPath();
    }

    public void startServiceList() {
        addToPath(FileDescriptorProto.SERVICE_FIELD_NUMBER);
    }

    public void endServiceList() {
        removeFromPath();
    }

    public void startServiceMethodList() {
        addToPath(ServiceDescriptorProto.METHOD_FIELD_NUMBER);
    }

    public void endServiceMethodList() {
        removeFromPath();
    }

    public void startMessageList() {
        addToPath(FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER);
    }

    public void endMessageList() {
        removeFromPath();
    }

    public void startNestedMessageList(String outerName) {
        addToPath(DescriptorProto.NESTED_TYPE_FIELD_NUMBER);
        outers.add(outerName);
    }

    public void endNestedMessageList() {
        outers.removeLast();
        removeFromPath();
    }

    public void startNestedEnumList(String outerName) {
        addToPath(DescriptorProto.ENUM_TYPE_FIELD_NUMBER);
        outers.add(outerName);
    }

    public void endNestedEnumList() {
        outers.removeLast();
        removeFromPath();
    }


    public void startListElement(int idx) {
        addToPath(idx);
    }

    public void endListElement() {
        removeFromPath();
    }

    private void addToPath(int index) {
        curPath.add(index);
    }

    private void removeFromPath() {
        curPath.removeLast();
    }

    // END - Path related methods.

    /**
     * Both this plugin and the protobuf Java compiler plugin encapsulate
     * all messages defined in a .proto in an outer class. This class
     * contains the original class name, and the one we use in our plugin.
     */
    public static class OuterClass {

        /**
         * The name the Java protobuf compiler uses to wrap around
         * its generated code for a given file descriptor.
         */
        private final String protoJavaClass;

        private final String pluginJavaClass;

        /**
         * If true, the java_multiple_files option is set in the file descriptor proto.
         * <p>
         * Summary of the option (from google/protobuf/descriptor.proto):
         * If set true, then the Java code generator will generate a separate .java
         * file for each top-level message, enum, and service defined in the .proto
         * file.  Thus, these types will *not* be nested inside the outer class
         * named by java_outer_classname.  However, the outer class will still be
         * generated to contain the file's getDescriptor() method as well as any
         * top-level extensions defined in the file.
         */
        private boolean multipleFilesEnabled;

        /**
         * Set to true during processing if an outer classname is not explicitly
         * specified and any message, service definition, or enum in the file has
         * the same name as the name of the file.
         * <p>
         * In those cases the protobuf java compiler appends "OuterClass" to the name
         * of its outer class, and we need to use the updated name in our generated code.
         * <p>
         * For example, if a file
         * TestMsg.proto contains:
         * message TestMsg { ... }
         * Then the generated protobuf Java class will be TestMsgOuterClass.
         */
        private boolean protoNameCollision = false;

        public OuterClass(@Nonnull final ProtocPluginCodeGenerator codeGenerator,
                          @Nonnull final FileDescriptorProto fileDescriptorProto) {
            this.protoJavaClass = getOriginalClass(fileDescriptorProto);
            this.pluginJavaClass = codeGenerator.generatePluginJavaClass(protoJavaClass);
            this.multipleFilesEnabled = fileDescriptorProto.getOptions().getJavaMultipleFiles();
        }

        private String getOriginalClass(FileDescriptorProto fileDescriptorProto) {
            if (fileDescriptorProto.getOptions().hasJavaOuterClassname()) {
                return fileDescriptorProto.getOptions().getJavaOuterClassname();
            }
            // Need to trim the folders.
            final String fileName = fileDescriptorProto.getName()
                    .substring(fileDescriptorProto.getName().lastIndexOf("/") + 1);

            // The protobuf Java compiler has some rules about how it converts
            // file names to outer class names. We try to replicate those rules
            // here, because we need to know the original class name to create
            // generated code that compiles.
            String originalClassName = fileName.replace(".proto", "");
            originalClassName = StringUtils.capitalize(originalClassName);
            if (originalClassName.contains("_")) {
                originalClassName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL,
                        StringUtils.lowerCase(originalClassName));
            }
            return originalClassName;
        }

        public String getProtoJavaClass() {
            return protoNameCollision ? protoJavaClass + "OuterClass" : protoJavaClass;
        }

        public String getPluginJavaClass() {
            return pluginJavaClass;
        }

        public boolean isMultipleFilesEnabled() {
            return multipleFilesEnabled;
        }

        /**
         * {@link AbstractDescriptor} instances should call this method at creation time.
         * This is used to track name collisions between descriptors and the outer class
         * during the same traversal as the {@link AbstractDescriptor} instantiation.
         *
         * @param name The name of the {@link AbstractDescriptor}.
         */
        public void onNewDescriptor(@Nonnull final String name) {
            if (name.equals(protoJavaClass)) {
                if (protoNameCollision && name.equals(getProtoJavaClass())) {
                    throw new IllegalStateException("Descriptor name " + name + " not allowed." +
                            "Protobuf compiler should have caught it.");
                }
                protoNameCollision = true;
            } else if (name.equals(pluginJavaClass)) {
                throw new IllegalArgumentException("Descriptor name " + name +
                        " not allowed. Reserved for REST generation.");
            }
        }
    }

}
