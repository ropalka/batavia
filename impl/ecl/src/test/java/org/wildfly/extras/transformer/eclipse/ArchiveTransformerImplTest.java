/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extras.transformer.eclipse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.wildfly.extras.transformer.ArchiveTransformer;
import org.wildfly.extras.transformer.TransformerFactory;

public class ArchiveTransformerImplTest {

    private static final String JAVAX_SERVLET = "javax/servlet/http/HttpServlet";
    private static final String JAKARTA_SERVLET = "jakarta/servlet/http/HttpServlet";
    private static final String JAVAX_REQUEST_DESC = "Ljavax/servlet/http/HttpServletRequest;";
    private static final String JAKARTA_REQUEST_DESC = "Ljakarta/servlet/http/HttpServletRequest;";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testTransformClassFile() throws Exception {
        byte[] classBytes = generateClassWithReferences(JAVAX_SERVLET, JAVAX_REQUEST_DESC);
        File inputFile = tempFolder.newFile("TestServlet.class");
        Files.write(inputFile.toPath(), classBytes);
        File outputFile = new File(tempFolder.getRoot(), "output/TestServlet.class");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer().build();
        boolean changed = transformer.transform(inputFile, outputFile);

        assertTrue("Transformation should report changes", changed);
        assertTrue("Output file should exist", outputFile.exists());

        ClassReader cr = new ClassReader(Files.readAllBytes(outputFile.toPath()));
        assertEquals(JAKARTA_SERVLET, cr.getSuperName());
        assertFieldDescriptor(cr, "request", JAKARTA_REQUEST_DESC);
    }

    @Test
    public void testTransformClassFileInvert() throws Exception {
        byte[] classBytes = generateClassWithReferences(JAKARTA_SERVLET, JAKARTA_REQUEST_DESC);
        File inputFile = tempFolder.newFile("TestServlet.class");
        Files.write(inputFile.toPath(), classBytes);
        File outputFile = new File(tempFolder.getRoot(), "output/TestServlet.class");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer()
                .setInvert(true)
                .build();
        boolean changed = transformer.transform(inputFile, outputFile);

        assertTrue("Invert transformation should report changes", changed);
        assertTrue("Output file should exist", outputFile.exists());

        ClassReader cr = new ClassReader(Files.readAllBytes(outputFile.toPath()));
        assertEquals(JAVAX_SERVLET, cr.getSuperName());
        assertFieldDescriptor(cr, "request", JAVAX_REQUEST_DESC);
    }

    @Test
    public void testClassFileNoChanges() throws Exception {
        byte[] classBytes = generateClassWithReferences("java/lang/Thread", "Ljava/lang/String;");
        File inputFile = tempFolder.newFile("PlainClass.class");
        Files.write(inputFile.toPath(), classBytes);
        File outputFile = new File(tempFolder.getRoot(), "output/PlainClass.class");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer().build();
        boolean changed = transformer.transform(inputFile, outputFile);

        assertFalse("Transformation should report no changes", changed);
        assertTrue("Output file should exist", outputFile.exists());

        ClassReader cr = new ClassReader(Files.readAllBytes(outputFile.toPath()));
        assertEquals("java/lang/Thread", cr.getSuperName());
    }

    @Test
    public void testTransformJarFile() throws Exception {
        byte[] classBytes = generateClassWithReferences(JAVAX_SERVLET, JAVAX_REQUEST_DESC);
        File inputJar = tempFolder.newFile("test-input.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(inputJar))) {
            JarEntry entry = new JarEntry("com/example/TestServlet.class");
            entry.setSize(classBytes.length);
            jos.putNextEntry(entry);
            jos.write(classBytes);
            jos.closeEntry();
        }
        File outputJar = new File(tempFolder.getRoot(), "test-output.jar");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer().build();
        boolean changed = transformer.transform(inputJar, outputJar);

        assertTrue("Transformation should report changes", changed);
        assertTrue("Output jar should exist", outputJar.exists());

        try (JarFile jar = new JarFile(outputJar)) {
            JarEntry entry = jar.getJarEntry("com/example/TestServlet.class");
            assertNotNull("Transformed class should exist in jar", entry);
            byte[] transformedBytes = jar.getInputStream(entry).readAllBytes();
            ClassReader cr = new ClassReader(transformedBytes);
            assertEquals(JAKARTA_SERVLET, cr.getSuperName());
            assertFieldDescriptor(cr, "request", JAKARTA_REQUEST_DESC);
        }
    }

    @Test
    public void testTransformJavaSourceFile() throws Exception {
        String javaxSource = "package com.example;\n"
                + "\n"
                + "import javax.servlet.http.HttpServlet;\n"
                + "import javax.servlet.http.HttpServletRequest;\n"
                + "import javax.servlet.http.HttpServletResponse;\n"
                + "\n"
                + "public class MyServlet extends HttpServlet {\n"
                + "    protected void doGet(HttpServletRequest request, HttpServletResponse response) {\n"
                + "    }\n"
                + "}\n";
        File inputFile = tempFolder.newFile("MyServlet.java");
        Files.write(inputFile.toPath(), javaxSource.getBytes(StandardCharsets.UTF_8));
        File outputFile = new File(tempFolder.getRoot(), "output/MyServlet.java");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer().build();
        transformer.transform(inputFile, outputFile);

        assertTrue("Output file should exist", outputFile.exists());
        String transformed = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain jakarta.servlet imports", transformed.contains("import jakarta.servlet.http.HttpServlet;"));
        assertTrue("Should contain jakarta.servlet.http.HttpServletRequest import",
                transformed.contains("import jakarta.servlet.http.HttpServletRequest;"));
        assertTrue("Should contain jakarta.servlet.http.HttpServletResponse import",
                transformed.contains("import jakarta.servlet.http.HttpServletResponse;"));
        assertFalse("Should not contain javax.servlet references", transformed.contains("javax.servlet"));
    }

    @Test
    public void testTransformJavaSourceFileInvert() throws Exception {
        String jakartaSource = "package com.example;\n"
                + "\n"
                + "import jakarta.servlet.http.HttpServlet;\n"
                + "import jakarta.servlet.http.HttpServletRequest;\n"
                + "\n"
                + "public class MyServlet extends HttpServlet {\n"
                + "    protected void doGet(HttpServletRequest request) {\n"
                + "    }\n"
                + "}\n";
        File inputFile = tempFolder.newFile("MyServlet.java");
        Files.write(inputFile.toPath(), jakartaSource.getBytes(StandardCharsets.UTF_8));
        File outputFile = new File(tempFolder.getRoot(), "output/MyServlet.java");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer()
                .setInvert(true)
                .build();
        transformer.transform(inputFile, outputFile);

        assertTrue("Output file should exist", outputFile.exists());
        String transformed = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain javax.servlet imports", transformed.contains("import javax.servlet.http.HttpServlet;"));
        assertTrue("Should contain javax.servlet.http.HttpServletRequest import",
                transformed.contains("import javax.servlet.http.HttpServletRequest;"));
        assertFalse("Should not contain jakarta.servlet references", transformed.contains("jakarta.servlet"));
    }

    @Test
    public void testJavaSourceFileNoChanges() throws Exception {
        String plainSource = "package com.example;\n"
                + "\n"
                + "import java.util.List;\n"
                + "import java.util.ArrayList;\n"
                + "\n"
                + "public class PlainClass {\n"
                + "    private List<String> items = new ArrayList<>();\n"
                + "}\n";
        File inputFile = tempFolder.newFile("PlainClass.java");
        Files.write(inputFile.toPath(), plainSource.getBytes(StandardCharsets.UTF_8));
        File outputFile = new File(tempFolder.getRoot(), "output/PlainClass.java");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer().build();
        transformer.transform(inputFile, outputFile);

        assertTrue("Output file should exist", outputFile.exists());
        String transformed = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertEquals("Source should be unchanged", plainSource, transformed);
    }

    @Test
    public void testTransformPropertiesFile() throws Exception {
        String javaxProperties = "# Server configuration\n"
                + "servlet.class=javax.servlet.http.HttpServlet\n"
                + "listener.class=javax.servlet.ServletContextListener\n"
                + "filter.class=javax.servlet.Filter\n"
                + "app.name=MyApplication\n"
                + "app.version=1.0\n";
        File inputFile = tempFolder.newFile("config.properties");
        Files.write(inputFile.toPath(), javaxProperties.getBytes(StandardCharsets.UTF_8));
        File outputFile = new File(tempFolder.getRoot(), "output/config.properties");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer().build();
        transformer.transform(inputFile, outputFile);

        assertTrue("Output file should exist", outputFile.exists());
        String transformed = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain jakarta.servlet.http.HttpServlet",
                transformed.contains("servlet.class=jakarta.servlet.http.HttpServlet"));
        assertTrue("Should contain jakarta.servlet.ServletContextListener",
                transformed.contains("listener.class=jakarta.servlet.ServletContextListener"));
        assertTrue("Should contain jakarta.servlet.Filter",
                transformed.contains("filter.class=jakarta.servlet.Filter"));
        assertFalse("Should not contain javax.servlet references", transformed.contains("javax.servlet"));
        assertTrue("Non-javax properties should be unchanged", transformed.contains("app.name=MyApplication"));
    }

    @Test
    public void testTransformPropertiesFileInvert() throws Exception {
        String jakartaProperties = "servlet.class=jakarta.servlet.http.HttpServlet\n"
                + "listener.class=jakarta.servlet.ServletContextListener\n";
        File inputFile = tempFolder.newFile("config.properties");
        Files.write(inputFile.toPath(), jakartaProperties.getBytes(StandardCharsets.UTF_8));
        File outputFile = new File(tempFolder.getRoot(), "output/config.properties");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer()
                .setInvert(true)
                .build();
        transformer.transform(inputFile, outputFile);

        assertTrue("Output file should exist", outputFile.exists());
        String transformed = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertTrue("Should contain javax.servlet.http.HttpServlet",
                transformed.contains("servlet.class=javax.servlet.http.HttpServlet"));
        assertTrue("Should contain javax.servlet.ServletContextListener",
                transformed.contains("listener.class=javax.servlet.ServletContextListener"));
        assertFalse("Should not contain jakarta.servlet references", transformed.contains("jakarta.servlet"));
    }

    @Test
    public void testPropertiesFileNoChanges() throws Exception {
        String plainProperties = "# Plain configuration\n"
                + "app.name=MyApplication\n"
                + "app.version=1.0\n"
                + "java.util.logging.level=INFO\n";
        File inputFile = tempFolder.newFile("plain.properties");
        Files.write(inputFile.toPath(), plainProperties.getBytes(StandardCharsets.UTF_8));
        File outputFile = new File(tempFolder.getRoot(), "output/plain.properties");

        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer().build();
        transformer.transform(inputFile, outputFile);

        assertTrue("Output file should exist", outputFile.exists());
        String transformed = new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
        assertEquals("Properties should be unchanged", plainProperties, transformed);
    }

    @Test
    public void testCanTransformIndividualClassFile() {
        ArchiveTransformer transformer = TransformerFactory.getInstance().newTransformer().build();
        assertTrue("Eclipse transformer should support individual class files",
                transformer.canTransformIndividualClassFile());
    }

    private byte[] generateClassWithReferences(String superClass, String fieldDescriptor) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, "com/example/TestClass", null, superClass, null);
        cw.visitField(Opcodes.ACC_PRIVATE, "request", fieldDescriptor, null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void assertFieldDescriptor(ClassReader cr, String fieldName, String expectedDescriptor) {
        String[] found = new String[1];
        cr.accept(new ClassVisitor(Opcodes.ASM7) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (fieldName.equals(name)) {
                    found[0] = descriptor;
                }
                return null;
            }
        }, 0);
        assertEquals("Field '" + fieldName + "' descriptor", expectedDescriptor, found[0]);
    }
}
