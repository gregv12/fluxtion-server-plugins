package com.fluxtion.server.plugin.eventhandlerloader;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class DynamicCompiler {
    public static void main(String[] args) throws Exception {
        // Files to compile
        File[] files = {new File("C:\\Users\\gregp\\IdeaProjects\\fluxtion-server-plugins\\serverplugin-javabuilder\\src\\main\\resources\\HelloWorld.java"), new File("C:\\Users\\gregp\\IdeaProjects\\fluxtion-server-plugins\\serverplugin-javabuilder\\src\\main\\resources\\Greeter.java")};

        // Get the Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // Prepare the file manager
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        File classesOutput = new File("C:\\Users\\gregp\\IdeaProjects\\fluxtion-server-plugins\\serverplugin-javabuilder\\src\\main\\resources\\");
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesOutput));

        // Compile the files
        compiler.getTask(null, fileManager, null, null, null, fileManager.getJavaFileObjects(files)).call();

        // Load the compiled classes
        URL[] urls = new URL[]{classesOutput.toURI().toURL()};
        URLClassLoader classLoader = URLClassLoader.newInstance(urls);

        // Load classes and create instances
        Class<?> helloWorldClass = classLoader.loadClass("HelloWorld");
        Object helloWorld = helloWorldClass.getDeclaredConstructor().newInstance();
        Class<?> greeterClass = classLoader.loadClass("Greeter");
        Object greeter = greeterClass.getDeclaredConstructor().newInstance();

        // Assuming each class has a method 'sayHello'
        helloWorldClass.getMethod("sayHello").invoke(helloWorld);
        greeterClass.getMethod("sayHello").invoke(greeter);

        fileManager.close();
    }
}

