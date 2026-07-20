package tech.kzen.lib.server.objects.reflective;

import tech.kzen.lib.common.reflect.Reflect;
import tech.kzen.lib.common.reflect.Service;
import tech.kzen.lib.server.objects.service.SampleService;

/**
 * A pure-Java @Reflect fixture, served only by the JVM reflective fallback: KSP registers Kotlin
 * sources, so this pins Java classloading, -parameters name extraction and @Service detection on a
 * Java constructor. See GlobalMirrorFallbackTest.
 */
@Reflect
public class JavaServiceHolder {
    private final String label;
    private final SampleService service;

    public JavaServiceHolder(String label, @Service SampleService service) {
        this.label = label;
        this.service = service;
    }

    public String getLabel() {
        return label;
    }

    public SampleService getService() {
        return service;
    }
}
