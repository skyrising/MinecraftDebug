package de.skyrising.minecraft.deobf;

import java.util.Collection;

public interface Mappings {
    String deobfuscateClass(String className);
    String obfuscateClass(String className);
    String deobfuscateMethod(String className, String method, String signature);
    Collection<String> getObfuscatedMethods(String className, String method);
    String obfuscateMethod(String className, String method, String signature);
    Collection<String> getDeobfuscatedMethods(String className, String method);
    String deobfuscateField(String className, String fieldName);
    String obfuscateField(String className, String fieldName);
}
