package de.skyrising.minecraft.deobf;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class TinyMappings implements Mappings {
    private final String[] namespaces;
    private final Object2IntMap<String>[] classMaps;
    private final List<String>[] classLists;
    private final Object2IntMap<String>[] methodMaps;
    private final Multimap<String, Integer>[] methodNames;
    private final List<String>[] methodNameLists;
    private final List<String>[] methodSignatureLists;
    private final Object2IntMap<String>[] fieldMaps;
    private final List<String>[] fieldLists;
    private int namespaceFrom = 0;
    private int namespaceTo;

    private TinyMappings(String header) {
        String[] splitHeader = header.split("\\s+");
        namespaces = Arrays.copyOfRange(splitHeader, 1, splitHeader.length);
        namespaceTo = namespaces.length - 1;
        classMaps = new Object2IntMap[namespaces.length];
        classLists = new List[namespaces.length];
        methodMaps = new Object2IntMap[namespaces.length];
        methodNames = new Multimap[namespaces.length];
        methodNameLists = new List[namespaces.length];
        methodSignatureLists = new List[namespaces.length];
        fieldMaps = new Object2IntMap[namespaces.length];
        fieldLists = new List[namespaces.length];
        for (int i = 0; i < namespaces.length; i++) {
            classMaps[i] = new Object2IntLinkedOpenHashMap<>();
            classLists[i] = new ArrayList<>();
            methodMaps[i] = new Object2IntLinkedOpenHashMap<>();
            methodNames[i] = MultimapBuilder.hashKeys().arrayListValues().build();
            methodNameLists[i] = new ArrayList<>();
            methodSignatureLists[i] = new ArrayList<>();
            fieldMaps[i] = new Object2IntLinkedOpenHashMap<>();
            fieldLists[i] = new ArrayList<>();
        }
    }

    public static TinyMappings load(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line = reader.readLine();
        TinyMappings mappings = new TinyMappings(line);
        while ((line = reader.readLine()) != null) {
            mappings.loadLine(line);
        }
        mappings.finishLoading();
        return mappings;
    }

    private void loadLine(String line) {
        String[] split = line.split("\\s+");
        switch (split[0]) {
            case "CLASS": {
                int classIndex = classLists[0].size();
                for (int i = 1; i < split.length; i++) {
                    int nsIndex = i - 1;
                    classMaps[nsIndex].put(split[i], classIndex);
                    classLists[nsIndex].add(split[i]);
                }
                break;
            }
            case "METHOD": {
                for (int i = 3; i < split.length; i++) {
                    int nsIndex = i - 3;
                    String key = nsIndex == 0 ? split[1] + " " + split[2] + " " + split[i] : split[i];
                    methodNameLists[nsIndex].add(key);
                }
                break;
            }
            case "FIELD": {
                for (int i = 3; i < split.length; i++) {
                    int nsIndex = i - 3;
                    String key = nsIndex == 0 ? split[1] + " " + split[i] : split[i];
                    fieldLists[nsIndex].add(key);
                }
                break;
            }
        }
    }

    private void finishLoading() {
        for (int methodIndex = 0; methodIndex < methodNameLists[0].size(); methodIndex++) {
            String key0 = methodNameLists[0].get(methodIndex);
            String[] keySplit = key0.split(" ");
            methodMaps[0].put(key0, methodIndex);
            methodNameLists[0].set(methodIndex, keySplit[2]);
            methodSignatureLists[0].add(keySplit[1]);
            methodNames[0].put(keySplit[0] + " " + keySplit[2], methodIndex);
            for (int i = 1; i < namespaces.length; i++) {
                String className = renameClass(keySplit[0], 0, i);
                String sig = renameSignature(keySplit[1], 0, i);
                String name = methodNameLists[i].get(methodIndex);
                String key = className + " " + sig + " " + name;
                methodMaps[i].put(key, methodIndex);
                methodSignatureLists[i].add(name);
                methodNames[i].put(className + " " + name, methodIndex);
            }
        }
        for (int fieldIndex = 0; fieldIndex < fieldLists[0].size(); fieldIndex++) {
            String key0 = fieldLists[0].get(fieldIndex);
            String[] keySplit = key0.split(" ");
            fieldMaps[0].put(key0, fieldIndex);
            for (int i = 1; i < namespaces.length; i++) {
                String className = renameClass(keySplit[0], 0, i);
                String name = fieldLists[i].get(fieldIndex);
                String key = className + " " + name;
                fieldMaps[i].put(key, fieldIndex);
            }
        }
    }

    private String renameClass(String className, int nsFrom, int nsTo) {
        int index = classMaps[nsFrom].getOrDefault(className, -1);
        if (index < 0) return null;
        return classLists[nsTo].get(index);
    }

    private String renameSignature(String sig, int nsFrom, int nsTo) {
        StringBuilder renamedSig = new StringBuilder();
        for (int i = 0; i < sig.length(); i++) {
            char c = sig.charAt(i);
            if (c != 'L') {
                renamedSig.append(c);
                continue;
            }
            int end = sig.indexOf(';', i + 1);
            String className = renameClass(sig.substring(i + 1, end), nsFrom, nsTo);
            renamedSig.append('L').append(className).append(';');
            i = end;
        }
        return renamedSig.toString();
    }

    @Override
    public String deobfuscateClass(String className) {
        return renameClass(className, namespaceFrom, namespaceTo);
    }

    @Override
    public String obfuscateClass(String className) {
        return renameClass(className, namespaceTo, namespaceFrom);
    }

    private String renameMethod(String className, String method, String signature, int nsFrom, int nsTo) {
        int index = methodMaps[nsFrom].getOrDefault(className + " " + signature + " " + method, -1);
        if (index < 0) return null;
        return methodNameLists[nsTo].get(index);
    }

    @Override
    public String deobfuscateMethod(String className, String method, String signature) {
        return renameMethod(className, method, signature, namespaceFrom, namespaceTo);
    }

    private List<String> getMethods(String className, String method, int namespace) {
        Collection<Integer> indexes = methodNames[namespace].get(className + " " + method);
        ArrayList<String> names = new ArrayList<>(indexes.size());
        for (Integer index : indexes) names.add(methodSignatureLists[namespace].get(index));
        return names;
    }

    @Override
    public Collection<String> getObfuscatedMethods(String className, String method) {
        return getMethods(className, method, namespaceFrom);
    }

    @Override
    public String obfuscateMethod(String className, String method, String signature) {
        return renameMethod(className, method, signature, namespaceTo, namespaceFrom);
    }

    @Override
    public Collection<String> getDeobfuscatedMethods(String className, String method) {
        return getMethods(className, method, namespaceTo);
    }

    private String renameField(String className, String field, int nsFrom, int nsTo) {
        int index = fieldMaps[nsFrom].getOrDefault(className + " " + field, -1);
        if (index < 0) return null;
        return fieldLists[nsTo].get(index);
    }

    @Override
    public String deobfuscateField(String className, String fieldName) {
        return renameField(className, fieldName, namespaceFrom, namespaceTo);
    }

    @Override
    public String obfuscateField(String className, String fieldName) {
        return renameField(className, fieldName, namespaceTo, namespaceFrom);
    }
}
