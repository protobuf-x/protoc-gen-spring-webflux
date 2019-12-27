package io.protobufx.protoc.gen.spring;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 *
 * The syntax of the path template is as follows:
 *
 *     Template = "/" Segments [ Verb ] ;
 *     Segments = Segment { "/" Segment } ;
 *     Segment  = "*" | "**" | LITERAL | Variable ;
 *     Variable = "{" FieldPath [ "=" Segments ] "}" ;
 *     FieldPath = IDENT { "." IDENT } ;
 *     Verb     = ":" LITERAL ;
 */
public class PathTemplate {

    private static final Logger log = LogManager.getLogger();

    private final Set<String> boundVariables;

    private final List<Segment> segments;

    public PathTemplate(@Nonnull final String template) {
        final String strippedTemplate = StringUtils.strip(template);
        if (strippedTemplate.isEmpty()) {
            throw new IllegalArgumentException("Template must not be empty!");
        } else if (!strippedTemplate.startsWith("/")) {
            throw new IllegalArgumentException("Template " + template + " must start with /!");
        }
        final String trimmedTemplate = strippedTemplate.substring(1);
        final String[] segmentsAndVerb = trimmedTemplate.split(":");
        if (segmentsAndVerb.length > 2) {
            throw new IllegalArgumentException(": must only be used to separate segments from verb.");
        }
        this.segments = Stream.of(segmentsAndVerb[0].split("/"))
                .map(Segment::new)
                .collect(toList());
        this.boundVariables = segments.stream()
                .map(Segment::getFieldPath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toSet());
    }

    @Nonnull
    public Set<String> getBoundVariables() {
        return Collections.unmodifiableSet(boundVariables);
    }

    @Nonnull
    public String getQueryPath() {
        // For now, because we don't support variable assignments or wildcards, this is very simple.
        return "/" + segments.stream()
                .map(segment -> {
                    if (segment.getFieldPath().isPresent()) {
                        return "{" + variableForPath(segment.getFieldPath().get()) + "}";
                    } else {
                        return segment.getLiteral().orElseThrow(() ->
                                new IllegalStateException("Should have field path or literal."));
                    }
                })
                .collect(Collectors.joining("/"));
    }


    @Nonnull
    private String variableForPath(@Nonnull final String path) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, path.replace(".", "_"));
    }

    private static class Segment {

        private final Optional<String> fieldPath;
        private final Optional<String> literal;

        public Segment(final String segment) {
            String fieldPath = null;
            String literal = null;

            final String strippedSegment = StringUtils.strip(segment);
            if (strippedSegment.equals("*")) {
                // Exactly one path element.
                throw new IllegalArgumentException("* Not supported in segment.");
            } else if (strippedSegment.equals("**")) {
                // One or more path elements.
                throw new IllegalArgumentException("** Not supported in segment.");
            } else if (strippedSegment.startsWith("{")) {
                // Variable.
                Preconditions.checkArgument(strippedSegment.endsWith("}"));
                // Trim the braces.
                final String variable = strippedSegment.substring(1, strippedSegment.length() - 1);
                fieldPath = variable.split("=")[0];
                if (variable.contains("=")) {
                    log.warn("Unsupported assignment of segment to variable: {}", variable);
                }
            } else {
                // Literal.
                literal = strippedSegment;
            }
            this.fieldPath = Optional.ofNullable(fieldPath);
            this.literal = Optional.ofNullable(literal);
            Preconditions.checkArgument(this.fieldPath.isPresent() || this.literal.isPresent());
        }

        public Optional<String> getFieldPath() {
            return fieldPath;
        }

        public Optional<String> getLiteral() {
            return literal;
        }

        @Override
        public String toString() {
            Preconditions.checkArgument(this.fieldPath.isPresent() || this.literal.isPresent());
            return fieldPath.map(s -> "{" + s + "}")
                    .orElseGet(literal::get);
        }
    }

}



