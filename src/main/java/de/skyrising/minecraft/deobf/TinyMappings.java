package de.skyrising.minecraft.deobf;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import de.skyrising.util.StringView;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class TinyMappings implements Mappings {
    private final String[] namespaces;
    private final Object2IntMap<StringView>[] classMaps;
    private final List<StringView>[] classLists;
    private final Object2IntMap<MemberInfo>[] methodMaps;
    private final Multimap<MemberInfo, Integer>[] methodNames;
    private final List<StringView>[] methodNameLists;
    private final List<StringView>[] methodSignatureLists;
    private final Object2IntMap<MemberInfo>[] fieldMaps;
    private final List<StringView>[] fieldLists;
    private int namespaceFrom = 0;
    private int namespaceTo;

    private TinyMappings(String header) {
        String[] splitHeader = header.split("\t");
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
            classMaps[i] = new Object2IntOpenHashMap<>();
            classLists[i] = new ArrayList<>();
            methodNames[i] = MultimapBuilder.hashKeys().arrayListValues().build();
            methodNameLists[i] = new ArrayList<>();
            methodSignatureLists[i] = new ArrayList<>();
            fieldLists[i] = new ArrayList<>();
        }
    }

    public static TinyMappings load(InputStream stream) throws IOException {
        try {
            long start = System.nanoTime();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line = reader.readLine();
            TinyMappings mappings = new TinyMappings(line);
            List<MemberInfo> fields = new ArrayList<>();
            List<MemberInfo> methods = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                mappings.loadLine(line, methods, fields);
            }
            for (int i = 0; i < mappings.namespaces.length; i++) {
                mappings.methodMaps[i] = new Object2IntOpenHashMap<>(methods.size());
                mappings.fieldMaps[i] = new Object2IntOpenHashMap<>(fields.size());
            }
            mappings.finishLoading(methods, fields);
            System.out.printf("Loaded mappings in %.3fms: %d classes, %d methods, %d fields\n",
                    (System.nanoTime() - start) / 1e6, mappings.classLists[0].size(), methods.size(), fields.size());
            return mappings;
        } finally {
            stream.close();
        }
    }

    private void loadLine(String line, List<MemberInfo> methods, List<MemberInfo> fields) {
        StringView[] split = StringView.split(line, '\t');
        switch (split[0].toString()) {
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
                    if (nsIndex == 0) methods.add(new MemberInfo(split[1], split[i], split[2]));
                    methodNameLists[nsIndex].add(split[i]);
                }
                break;
            }
            case "FIELD": {
                for (int i = 3; i < split.length; i++) {
                    int nsIndex = i - 3;
                    if (nsIndex == 0) fields.add(new MemberInfo(split[1], split[i]));
                    fieldLists[nsIndex].add(split[i]);
                }
                break;
            }
        }
    }

    private void finishLoading(List<MemberInfo> methods, List<MemberInfo> fields) {
        for (int methodIndex = 0; methodIndex < methods.size(); methodIndex++) {
            finishLoadingMethod(methodIndex, methods.get(methodIndex));
        }
        for (int fieldIndex = 0; fieldIndex < fields.size(); fieldIndex++) {
            finishLoadingField(fieldIndex, fields.get(fieldIndex));
        }
        /*
        for (int i = 0; i < namespaces.length; i++) {
            Object2IntMap<MemberInfo> methodMap = methodMaps[i];
            try {
                Field n = methodMap.getClass().getDeclaredField("n");
                n.setAccessible(true);
                int size = n.getInt(methodMap);
                System.out.printf("%d: %d\n", i, size);
                int[] hashCount = new int[size];
                Multimap<Integer, MemberInfo> collisions = MultimapBuilder.hashKeys().arrayListValues().build();
                for (MemberInfo m : methodMap.keySet()) {
                    int pos = HashCommon.mix(m.hashCode()) & (size - 1);
                    hashCount[pos]++;
                    collisions.put(pos, m);
                }
                IntSummaryStatistics summaryStatistics = Arrays.stream(hashCount).summaryStatistics();
                System.out.println(summaryStatistics);
                for (int j = 0; j < size; j++) {
                    if (hashCount[j] != summaryStatistics.getMax()) continue;
                    System.out.println(Integer.toHexString(j));
                    for (MemberInfo m : collisions.get(j)) {
                        System.out.println("  " + Integer.toBinaryString(m.hashCode()) + ", " + Integer.toHexString(HashCommon.mix(m.hashCode())) + ": " + m);
                    }
                }
            } catch (ReflectiveOperationException e) {}
        }
        */
    }

    private void finishLoadingMethod(int index, MemberInfo method) {
        for (int i = 0; i < namespaces.length; i++) {
            StringView className = renameClass(method.className, 0, i);
            StringView sig = renameSignature(method.descriptor, 0, i);
            StringView name = methodNameLists[i].get(index);
            methodMaps[i].put(new MemberInfo(className, name, sig), index);
            methodSignatureLists[i].add(name);
            methodNames[i].put(new MemberInfo(className, name), index);
        }
    }

    private void finishLoadingField(int index, MemberInfo field) {
        for (int i = 0; i < namespaces.length; i++) {
            StringView className = renameClass(field.className, 0, i);
            StringView name = fieldLists[i].get(index);
            fieldMaps[i].put(new MemberInfo(className, name), index);
        }
    }

    private StringView renameClass(StringView className, int nsFrom, int nsTo) {
        int index = classMaps[nsFrom].getOrDefault(className, -1);
        if (index < 0) return null;
        return classLists[nsTo].get(index);
    }

    private StringView renameSignature(StringView sig, int nsFrom, int nsTo) {
        if (nsFrom == nsTo) return sig;
        StringBuilder renamedSig = new StringBuilder(sig.length());
        for (int i = 0; i < sig.length(); i++) {
            char c = sig.charAt(i);
            if (c != 'L') {
                renamedSig.append(c);
                continue;
            }
            int end = i + 1;
            for (; end < sig.length(); end++) {
                if (sig.charAt(end) == ';') break;
            }
            StringView obfClassName = sig.subSequence(i + 1, end);
            StringView className = renameClass(obfClassName, nsFrom, nsTo);
            renamedSig.append('L').append(className == null ? obfClassName : className).append(';');
            i = end;
        }
        return new StringView(renamedSig.toString());
    }

    @Override
    public String deobfuscateClass(String className) {
        return Objects.toString(renameClass(new StringView(className), namespaceFrom, namespaceTo), null);
    }

    @Override
    public String obfuscateClass(String className) {
        return Objects.toString(renameClass(new StringView(className), namespaceTo, namespaceFrom), null);
    }

    private String renameMethod(String className, String method, String signature, int nsFrom, int nsTo) {
        if (className == null || method == null || signature == null) return null;
        int index = methodMaps[nsFrom].getOrDefault(new MemberInfo(className, method, signature), -1);
        if (index < 0) return null;
        return methodNameLists[nsTo].get(index).toString();
    }

    @Override
    public String deobfuscateMethod(String className, String method, String signature) {
        return renameMethod(className, method, signature, namespaceFrom, namespaceTo);
    }

    private List<String> getMethods(String className, String method, int namespace) {
        Collection<Integer> indexes = methodNames[namespace].get(new MemberInfo(className, method));
        ArrayList<String> names = new ArrayList<>(indexes.size());
        for (Integer index : indexes) names.add(methodSignatureLists[namespace].get(index).toString());
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
        int index = fieldMaps[nsFrom].getOrDefault(new MemberInfo(className, field), -1);
        if (index < 0) return null;
        return fieldLists[nsTo].get(index).toString();
    }

    @Override
    public String deobfuscateField(String className, String fieldName) {
        return renameField(className, fieldName, namespaceFrom, namespaceTo);
    }

    @Override
    public String obfuscateField(String className, String fieldName) {
        return renameField(className, fieldName, namespaceTo, namespaceFrom);
    }

    private static final class MemberInfo /* implements Comparable<MemberInfo>*/ {
        final StringView className;
        final StringView memberName;
        final StringView descriptor;

        MemberInfo(StringView className, StringView memberName) {
            this(className, memberName, null);
        }

        MemberInfo(String className, String memberName) {
            this(new StringView(className), new StringView(memberName));
        }

        MemberInfo(StringView className, StringView memberName, StringView descriptor) {
            this.className = className;
            this.memberName = memberName;
            this.descriptor = descriptor;
        }

        MemberInfo(String className, String memberName, String descriptor) {
            this(new StringView(className), new StringView(memberName), new StringView(descriptor));
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MemberInfo)) return false;
            MemberInfo m = (MemberInfo) obj;
            return this.className.equals(m.className)
                    && this.memberName.equals(m.memberName)
                    && Objects.equals(this.descriptor, m.descriptor);
        }

        @Override
        public int hashCode() {
            return ((Objects.hashCode(descriptor) * 31 + memberName.hashCode()) * 31) + className.hashCode();
        }

        /*
        @Override
        public int compareTo(MemberInfo o) {
            int cComp = this.className.compareTo(o.className);
            if (cComp != 0) return cComp;
            int mComp = this.memberName.compareTo(o.memberName);
            if (mComp != 0) return mComp;
            if (this.descriptor == null) return o.descriptor == null ? 0 : -1;
            if (o.descriptor == null) return 1;
            return this.descriptor.compareTo(o.descriptor);
        }
        */

        @Override
        public String toString() {
            return className + "." + memberName + (descriptor != null ? descriptor : "");
        }
    }
}
