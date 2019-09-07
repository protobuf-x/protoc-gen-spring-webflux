package io.disc99.protoc.gen.spring.generator;

import java.util.*;

public class Parameters {

    Map<String, String> parameters = new HashMap<>();

    public Parameters() {
    }

    public void add(String parameter) {
        Arrays.stream(parameter.split(","))
                .forEach(p -> {
                    String[] parameterAndValue = p.split("=");
                    if (!(parameterAndValue.length == 1 || parameterAndValue.length == 2)) {
                        throw new IllegalArgumentException("Invalid build parameter: " + p);
                    }
                    parameters.put(parameterAndValue[0], parameterAndValue.length == 1 ? null : parameterAndValue[1]);
                });
    }

    public boolean hasParamater(String parameter) {
        return parameters.containsKey(parameter);
    }

    public String getParameterValue(String parameterKey) {
        return parameters.get(parameterKey);
    }
}
