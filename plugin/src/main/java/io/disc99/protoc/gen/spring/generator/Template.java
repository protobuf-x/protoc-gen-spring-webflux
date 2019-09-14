package io.disc99.protoc.gen.spring.generator;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import lombok.SneakyThrows;

import java.util.Map;

public class Template {
    @SneakyThrows
    public static String apply(String file, Map<String, Object> context) {
        TemplateLoader loader = new ClassPathTemplateLoader();
        Handlebars handlebars = new Handlebars(loader).prettyPrint(true).with(EscapingStrategy.NOOP);
        return handlebars.compile(file).apply(context);
    }
}
