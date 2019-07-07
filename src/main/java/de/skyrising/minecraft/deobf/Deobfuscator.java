package de.skyrising.minecraft.deobf;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;

public class Deobfuscator {
    private Mappings mappings;
    private ClassLoader classLoader;
    private Set<String> classesParsed = new HashSet<>();
    private Map<String, Object[]> classLinesDescMap = new HashMap<>();
    private Map<String, String> superClassMap = new HashMap<>();
    private Map<String, String[]> interfaceMap = new HashMap<>();

    public Deobfuscator(Mappings mappings, ClassLoader classLoader) {
        this.mappings = mappings;
        this.classLoader = classLoader;
    }

    public StackTraceElement deobfuscate(StackTraceElement ste) {
        String obfClassName = ste.getClassName().replace('.', '/');
        String suffix = "";
        if (obfClassName.contains("$Lambda$")) {
            int index = obfClassName.indexOf("$Lambda$");
            suffix = obfClassName.substring(index);
            obfClassName = obfClassName.substring(0, index);
            if (obfClassName.endsWith("$")) {
                obfClassName = obfClassName.substring(0, index - 1);
                suffix = "$" + suffix;
            }
        }
        String className = mappings.deobfuscateClass(obfClassName);
        if (className == null) return ste;
        String outerClassName = className.contains("$") ? className.substring(0, className.indexOf('$')) : className;
        String fileName = outerClassName.substring(outerClassName.lastIndexOf('/') + 1) + ".java";
        String obfMethodName = ste.getMethodName();
        String sig = findMethod(ste);
        String methodName = deobfuscateMethod(obfClassName, obfMethodName, sig);
        if (methodName == null) {
            return new StackTraceElement(className.replace('/', '.') + suffix, obfMethodName, fileName, ste.getLineNumber());
        }
        return new StackTraceElement(className.replace('/', '.') + suffix, methodName, fileName, ste.getLineNumber());
    }

    private String findMethod(StackTraceElement ste) {
        String internalClassName = ste.getClassName().replace('.', '/');
        if (!classesParsed.contains(internalClassName)) this.parseClass(internalClassName);
        Object[] lines = classLinesDescMap.get(internalClassName);
        int lineNumber = ste.getLineNumber();
        if (lines == null || lineNumber >= lines.length) return null;
        Object lineInfo = lines[lineNumber];
        if (lineInfo == null) return null;
        if (lineInfo instanceof String) return (String) lineInfo;
        return ((Map<String, String>) lineInfo).get(ste.getMethodName());
    }

    private void parseClass(String internalClassName) {
        classesParsed.add(internalClassName);
        String fileName = internalClassName + ".class";
        InputStream classBytesStream = classLoader.getResourceAsStream(fileName);
        if (classBytesStream == null) return;
        try {
            List<Object> linesDesc = new ArrayList<>();
            List<String> linesName = new ArrayList<>();
            ClassReader cr = new ClassReader(classBytesStream);
            cr.accept(new ClassVisitor(Opcodes.ASM7) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    superClassMap.put(name, superName);
                    interfaceMap.put(name, interfaces);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM7) {
                        @Override
                        public void visitLineNumber(int line, Label start) {
                            for (int i = linesDesc.size(); i <= line; i++) linesDesc.add(null);
                            for (int i = linesName.size(); i <= line; i++) linesName.add(null);
                            Object present = linesDesc.get(line);
                            if (present != null && !linesName.get(line).endsWith(name)) {
                                if (present instanceof String) {
                                    Map<String, String> descToName = new Object2ObjectArrayMap<>();
                                    descToName.put(linesName.get(line), (String) present);
                                    present = descToName;
                                    linesDesc.set(line, present);
                                }
                                assert present instanceof Map;
                                ((Map<String, String>) present).put(name, descriptor);
                                return;
                            }
                            linesDesc.set(line, descriptor);
                            linesName.set(line, name);
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
            classLinesDescMap.put(internalClassName, linesDesc.toArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String deobfuscateMethod(String className, String method, String signature) {
        // System.out.println(className + "." + method + signature);
        String deobfed = mappings.deobfuscateMethod(className, method, signature);
        if (deobfed != null) return deobfed;
        if (!classesParsed.contains(className)) this.parseClass(className);
        String superClass = superClassMap.get(className);
        if (superClass != null) {
            String superMethod = deobfuscateMethod(superClass, method, signature);
            if (superMethod != null) return superMethod;
        }
        String[] interfaces = interfaceMap.get(className);
        if (interfaces != null) {
            for (String interfaceName : interfaces) {
                String interfaceMethod = deobfuscateMethod(interfaceName, method, signature);
                if (interfaceMethod != null) return interfaceMethod;
            }
        }
        return null;
    }
}
