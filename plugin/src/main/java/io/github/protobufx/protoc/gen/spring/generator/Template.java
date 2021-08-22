package io.github.protobufx.protoc.gen.spring.generator;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;

import java.io.IOException;
import java.util.Map;

public class Template {
    public static String apply(String file, Map<String, Object> context) {
        TemplateLoader loader = new ClassPathTemplateLoader();
        Handlebars handlebars = new Handlebars(loader).prettyPrint(true).with(EscapingStrategy.NOOP);
        try {
            return handlebars.compile(file).apply(context);
        } catch (IOException e) {
            throw new IllegalStateException("Template apply error. file name: " + file + ", context: " + context);
        }
    }
}
