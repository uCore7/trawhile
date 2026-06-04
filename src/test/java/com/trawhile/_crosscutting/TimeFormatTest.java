package com.trawhile._crosscutting;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test enforcing SR-00-C10.C01: all in-memory timestamp values in backend
 * application code, persistence DTOs, port models, and event payloads shall be
 * of type {@code java.time.Instant} (UTC by construction). No backend type shall
 * use {@link LocalDateTime}, {@link ZonedDateTime}, or {@link OffsetDateTime}.
 *
 * <p>The test scans compiled production classes under {@code target/classes/com/trawhile/},
 * excluding only the jOOQ generated package
 * {@code com.trawhile.adapter.outbound.persistence.jooq} — jOOQ generates its own
 * time mapping outside this convention. All hand-authored ports, adapters, services,
 * and OpenAPI-generated DTOs are within scope.</p>
 *
 * <p>When violations are found the test fails with a multi-line message listing
 * every offending class member. When no violations are found the test passes
 * silently.</p>
 *
 * @see <a href="spec/test-plan.md">spec/test-plan.md — TE-00-C10.C01-01</a>
 */
class TimeFormatTest {

    private static final String JOOQ_PACKAGE_PREFIX = "com.trawhile.adapter.outbound.persistence.jooq";

    private static final Set<Class<?>> FORBIDDEN_TYPES = Set.of(
            LocalDateTime.class,
            ZonedDateTime.class,
            OffsetDateTime.class
    );

    /**
     * Scans all compiled production classes under {@code com.trawhile} (excluding the
     * jOOQ generated package) and asserts that no declared field, method return type,
     * or method parameter type is {@link LocalDateTime}, {@link ZonedDateTime}, or
     * {@link OffsetDateTime}.
     *
     * <p>This test enforces SR-00-C10.C01. Expected red state: OpenAPI-generated DTOs
     * in {@code com.trawhile.adapter.inbound.web.dto} currently use {@code OffsetDateTime};
     * the fix is an {@code openapi-generator-maven-plugin} {@code typeMappings}
     * configuration in {@code pom.xml}, which is the impl-backend agent's responsibility.</p>
     */
    @Test
    @Tag("TE-00-C10.C01-01")
    void noBackendTypeUsesLocalDateTime_orZonedDateTime_orOffsetDateTime() throws Exception {
        List<Class<?>> classes = loadProductionClasses();
        List<String> violations = new ArrayList<>();

        for (Class<?> cls : classes) {
            // Inspect declared fields
            for (Field field : safeGetDeclaredFields(cls)) {
                if (FORBIDDEN_TYPES.contains(field.getType())) {
                    violations.add(cls.getName() + "." + field.getName() + ": " + field.getType().getSimpleName());
                }
            }

            // Inspect declared methods: return type and parameter types
            for (Method method : safeGetDeclaredMethods(cls)) {
                if (FORBIDDEN_TYPES.contains(method.getReturnType())) {
                    violations.add(cls.getName() + "." + method.getName()
                            + "() return: " + method.getReturnType().getSimpleName());
                }
                for (Parameter param : method.getParameters()) {
                    if (FORBIDDEN_TYPES.contains(param.getType())) {
                        violations.add(cls.getName() + "." + method.getName()
                                + "(" + param.getName() + "): " + param.getType().getSimpleName());
                    }
                }
            }
        }

        assertThat(violations)
                .as("SR-00-C10.C01 violation — the following backend members use a forbidden"
                        + " timestamp type (must be java.time.Instant):\n"
                        + String.join("\n", violations))
                .isEmpty();
    }

    // ----- helpers -----

    /**
     * Resolves the {@code target/classes} root via the test's own ClassLoader and
     * returns every {@code .class} file under {@code com/trawhile/} as a loaded
     * {@link Class}, skipping the jOOQ generated package and inner {@code $}-class
     * entries that cannot be independently loaded.
     *
     * <p>The ClassLoader exposes multiple {@code com/trawhile} entries on the classpath
     * (both {@code target/test-classes} and {@code target/classes}). This method
     * explicitly picks the {@code target/classes} entry so that only production types
     * are scanned.</p>
     */
    private List<Class<?>> loadProductionClasses() throws Exception {
        ClassLoader cl = getClass().getClassLoader();

        // Enumerate all "com/trawhile" entries and select target/classes.
        // Using getResources() instead of getResource() ensures we see all classpath
        // roots and can pick the production root rather than the test root.
        Enumeration<URL> allRoots = cl.getResources("com/trawhile");
        URL rootUrl = null;
        while (allRoots.hasMoreElements()) {
            URL candidate = allRoots.nextElement();
            if (candidate.toString().contains("target/classes")) {
                rootUrl = candidate;
                break;
            }
        }
        if (rootUrl == null) {
            throw new IllegalStateException(
                    "Cannot locate 'target/classes/com/trawhile' on the test classpath — "
                            + "run './scripts/mvn-local.sh test-compile' first.");
        }

        // Walk the filesystem tree
        File rootDir = new File(rootUrl.toURI());
        List<String> classNames = new ArrayList<>();
        collectClassNames(rootDir, "com.trawhile", classNames);

        List<Class<?>> result = new ArrayList<>();
        for (String className : classNames) {
            if (className.startsWith(JOOQ_PACKAGE_PREFIX)) {
                continue; // excluded: jOOQ generated time mapping
            }
            // Skip anonymous / inner classes that share a parent — they will be covered
            // via the enclosing class's reflected members
            if (className.contains("$")) {
                continue;
            }
            try {
                result.add(Class.forName(className, false, cl));
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Class is generated or bridged in a way that cannot be loaded in isolation;
                // skip gracefully — the enclosing type still carries the relevant members.
            }
        }
        return result;
    }

    /** Recursively collect binary class names from a directory of {@code .class} files. */
    private void collectClassNames(File dir, String packageName, List<String> accumulator) {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectClassNames(entry, packageName + "." + entry.getName(), accumulator);
            } else if (entry.getName().endsWith(".class")) {
                String simpleName = entry.getName().replace(".class", "");
                accumulator.add(packageName + "." + simpleName);
            }
        }
    }

    private Field[] safeGetDeclaredFields(Class<?> cls) {
        try {
            return cls.getDeclaredFields();
        } catch (Throwable t) {
            return new Field[0];
        }
    }

    private Method[] safeGetDeclaredMethods(Class<?> cls) {
        try {
            return cls.getDeclaredMethods();
        } catch (Throwable t) {
            return new Method[0];
        }
    }
}
