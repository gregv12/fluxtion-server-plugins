/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.eventhandlerloader;

import com.fluxtion.agrona.LangUtil;
import com.fluxtion.agrona.SystemUtil;
import com.fluxtion.agrona.generation.CharSequenceJavaFileObject;
import com.fluxtion.compiler.EventProcessorConfig;
import com.fluxtion.compiler.Fluxtion;
import com.fluxtion.compiler.FluxtionGraphBuilder;
import com.fluxtion.compiler.generation.RuntimeConstants;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.partition.LambdaReflection;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import javax.tools.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;

public class DynamicCompiler {

    private static final String TEMP_DIR_NAME = SystemUtil.tmpDirName();

    public static EventProcessor<?> loadEventProcessor(
            boolean compileProcessor,
            Path javaSourceFilePath,
            Consumer<String> out,
            Consumer<String> err) throws IOException {

        FluxtionGraphBuilder builder = DynamicCompiler.compileBuilder(
                javaSourceFilePath,
                System.out::println,
                System.err::println);

//        Thread.currentThread().setContextClassLoader(builder.getClass().getClassLoader());

        final ArrayList<String> options = new ArrayList<>(Arrays.asList(
                "-classpath", System.getProperty("java.class.path") + File.pathSeparator + TEMP_DIR_NAME));

        String cp = System.getProperty(RuntimeConstants.GENERATION_CLASSPATH);

        System.setProperty(RuntimeConstants.GENERATION_CLASSPATH, System.getProperty("java.class.path") + File.pathSeparator + TEMP_DIR_NAME);

        LambdaReflection.SerializableConsumer<EventProcessorConfig> buildGraph = builder::buildGraph;
        return compileProcessor ? Fluxtion.compile(buildGraph) : Fluxtion.interpret(buildGraph);
    }

    public static FluxtionGraphBuilder compileBuilder(Path javaSourceFilePath, Consumer<String> out, Consumer<String> err) throws IOException {
        FluxtionGraphBuilder[] builder = new FluxtionGraphBuilder[1];
        String path = javaSourceFilePath.toAbsolutePath().toString();
        String simpleName = javaSourceFilePath.getFileName().toFile().getName().replace(".java", "");
        CompilationUnit cu = StaticJavaParser.parse(javaSourceFilePath);
        cu.findFirst(ClassOrInterfaceDeclaration.class, c -> c.getNameAsString().endsWith(simpleName))
                .map(ClassOrInterfaceDeclaration.class::cast)
                .ifPresent(classDeclaration -> {
                    String className = classDeclaration.getFullyQualifiedName().get();
                    out.accept("compiling builder class: " + className);
                    try {
                        @SuppressWarnings({"unchecked"})
                        Class<FluxtionGraphBuilder> compiledClass = (Class<FluxtionGraphBuilder>)
                                compileOnDisk(className, Map.of(className, Files.readString(javaSourceFilePath)));
                        builder[0] = compiledClass.getDeclaredConstructor().newInstance();
                    } catch (ClassNotFoundException | IOException | InvocationTargetException | NoSuchMethodException |
                             IllegalAccessException | InstantiationException e) {
                        err.accept("problem compiling FluxtionGraphBuilder path: " + path + "error:" + e.getMessage());
                    }
                });

        return builder[0];
    }


    public static Class<?> compileOnDisk(final String className, final Map<String, CharSequence> sources)
            throws ClassNotFoundException, IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (null == compiler) {
            throw new IllegalStateException("JDK required to run tests. JRE is not sufficient.");
        }

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            final ArrayList<String> options = new ArrayList<>(Arrays.asList(
                    "-classpath", System.getProperty("java.class.path") + File.pathSeparator + TEMP_DIR_NAME));

            final Collection<File> files = persist(sources);
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);
            final JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits);

            return compileAndLoad(className, diagnostics, fileManager, task);
        }
    }

    /**
     * Compile and load a class.
     *
     * @param className   name of the class to compile.
     * @param diagnostics attached to the compilation task.
     * @param fileManager to load compiled class from disk.
     * @param task        compilation task.
     * @return {@link Class} for the compiled class or {@code null} if compilation fails.
     * @throws ClassNotFoundException if compiled class was not loaded.
     */
    public static Class<?> compileAndLoad(
            final String className,
            final DiagnosticCollector<JavaFileObject> diagnostics,
            final JavaFileManager fileManager,
            final JavaCompiler.CompilationTask task) throws ClassNotFoundException {
        if (!compile(diagnostics, task)) {
            return null;
        }

        return fileManager.getClassLoader(StandardLocation.CLASS_OUTPUT).loadClass(className);
    }

    /**
     * Execute compilation task and report errors if it fails.
     *
     * @param diagnostics attached to the compilation task.
     * @param task        compilation to be executed.
     * @return {@code true} if compilation succeeds.
     */
    public static boolean compile(
            final DiagnosticCollector<JavaFileObject> diagnostics, final JavaCompiler.CompilationTask task) {
        final Boolean succeeded = task.call();

        if (!succeeded) {
            for (final Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                System.err.println(diagnostic.getCode());
                System.err.println(diagnostic.getKind());

                final JavaFileObject source = diagnostic.getSource();
                System.err.printf("Line = %d, Col = %d, File = %s",
                        diagnostic.getLineNumber(), diagnostic.getColumnNumber(), source);

                System.err.println("Start: " + diagnostic.getStartPosition());
                System.err.println("End: " + diagnostic.getEndPosition());
                System.err.println("Pos: " + diagnostic.getPosition());

                try {
                    final String content = source.getCharContent(true).toString();
                    final int begin = content.lastIndexOf('\n', (int) diagnostic.getStartPosition());
                    final int end = content.indexOf('\n', (int) diagnostic.getEndPosition());
                    System.err.println(diagnostic.getMessage(null));
                    System.err.println(content.substring(Math.max(0, begin), end));
                } catch (final IOException ex) {
                    LangUtil.rethrowUnchecked(ex);
                }
            }
        }

        return succeeded;
    }

    /**
     * Persist source files to disc.
     *
     * @param sources to persist.
     * @return a collection of {@link File} objects pointing to the persisted sources.
     * @throws IOException in case of I/O errors.
     */
    public static Collection<File> persist(final Map<String, CharSequence> sources) throws IOException {
        final Collection<File> files = new ArrayList<>(sources.size());
        for (final Map.Entry<String, CharSequence> entry : sources.entrySet()) {
            final String fqClassName = entry.getKey();
            String className = fqClassName;
            Path path = Paths.get(TEMP_DIR_NAME);

            final int indexOfLastDot = fqClassName.lastIndexOf('.');
            if (indexOfLastDot != -1) {
                className = fqClassName.substring(indexOfLastDot + 1);

                path = Paths.get(
                        TEMP_DIR_NAME + fqClassName.substring(0, indexOfLastDot).replace('.', File.separatorChar));
                Files.createDirectories(path);
            }

            final File file = new File(path.toString(), className + ".java");
            files.add(file);

            try (FileWriter out = new FileWriter(file)) {
                out.append(entry.getValue());
                out.flush();
            }
        }

        return files;
    }

    private static Collection<CharSequenceJavaFileObject> wrap(final Map<String, CharSequence> sources) {
        return sources
                .entrySet()
                .stream()
                .map((e) -> new CharSequenceJavaFileObject(e.getKey(), e.getValue()))
                .collect(toList());
    }
}

