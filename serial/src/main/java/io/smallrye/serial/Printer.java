package io.smallrye.serial;

import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

import io.smallrye.common.constraint.Assert;
import io.smallrye.serial.impl.IntMap;

/**
 * A utility for formatting the structure of {@link Serialized} object graphs as human-readable,
 * indented text.
 * <p>
 * The printer handles all concrete {@link Serialized} subtypes, tracks circular references
 * using numeric labels ({@code #1}, {@code #2}, etc.) and back-references ({@code <ref #1>}),
 * and formats byte data as grouped hex dumps and char data as 4-digit hex dumps with
 * Unicode sidebars.
 * <p>
 * Static convenience methods use a default configuration (4-space indent, unlimited depth,
 * class loader info shown). For custom configuration, use the {@link Builder}:
 *
 * <pre>{@code
 * Printer printer = Printer.builder()
 *         .indent(2)
 *         .maxDepth(5)
 *         .showClassLoader(false)
 *         .build();
 * String output = printer.printOne(serialized);
 * }</pre>
 * <p>
 * {@code Printer} instances are immutable and thread-safe; each print call creates
 * a fresh internal context for mutable state.
 */
public final class Printer {

    private static final String DEFAULT_INDENT = "    ";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String indentStr;
    private final int maxDepth;
    private final boolean showClassLoader;

    private Printer(final String indentStr, final int maxDepth, final boolean showClassLoader) {
        this.indentStr = indentStr;
        this.maxDepth = maxDepth;
        this.showClassLoader = showClassLoader;
    }

    // ---- Static convenience methods ----

    /**
     * Format a single {@link Serialized} value using the default configuration.
     *
     * @param serialized the value to format (must not be {@code null})
     * @return the formatted string (not {@code null})
     */
    public static String print(final Serialized serialized) {
        Assert.checkNotNullParam("serialized", serialized);
        return new Printer(DEFAULT_INDENT, Integer.MAX_VALUE, true).printOne(serialized);
    }

    /**
     * Format multiple {@link Serialized} values using the default configuration.
     * Each value is separated by a newline. Circular reference labels are shared
     * across all values.
     *
     * @param serialized the values to format (must not be {@code null})
     * @return the formatted string (not {@code null})
     */
    public static String print(final Serialized... serialized) {
        Assert.checkNotNullParam("serialized", serialized);
        return new Printer(DEFAULT_INDENT, Integer.MAX_VALUE, true).printAll(serialized);
    }

    // ---- Instance methods ----

    /**
     * Format a single {@link Serialized} value using this printer's configuration.
     *
     * @param serialized the value to format (must not be {@code null})
     * @return the formatted string (not {@code null})
     */
    public String printOne(final Serialized serialized) {
        Assert.checkNotNullParam("serialized", serialized);
        PrintContext ctx = new PrintContext();
        ctx.printValue(serialized);
        return ctx.sb.toString();
    }

    /**
     * Format multiple {@link Serialized} values using this printer's configuration.
     * Each value is separated by a newline. Circular reference labels are shared
     * across all values.
     *
     * @param serialized the values to format (must not be {@code null})
     * @return the formatted string (not {@code null})
     */
    public String printAll(final Serialized... serialized) {
        Assert.checkNotNullParam("serialized", serialized);
        PrintContext ctx = new PrintContext();
        for (int i = 0; i < serialized.length; i++) {
            if (i > 0) {
                ctx.sb.append('\n');
            }
            ctx.printValue(serialized[i]);
        }
        return ctx.sb.toString();
    }

    /**
     * Format a collection of {@link Serialized} values using this printer's configuration.
     * Each value is separated by a newline. Circular reference labels are shared
     * across all values.
     *
     * @param serialized the values to format (must not be {@code null})
     * @return the formatted string (not {@code null})
     */
    public String printAll(final Collection<? extends Serialized> serialized) {
        Assert.checkNotNullParam("serialized", serialized);
        return printAll(serialized.toArray(Serialized[]::new));
    }

    // ---- Builder ----

    /**
     * {@return a new builder for constructing a configured {@link Printer} instance}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for constructing {@link Printer} instances with custom configuration.
     */
    public static final class Builder {
        private String indentStr = DEFAULT_INDENT;
        private int maxDepth = Integer.MAX_VALUE;
        private boolean showClassLoader = true;

        private Builder() {
        }

        /**
         * Set the indent string used for each nesting level.
         *
         * @param indent the indent string (must not be {@code null})
         * @return this builder
         */
        public Builder indent(final String indent) {
            this.indentStr = Assert.checkNotNullParam("indent", indent);
            return this;
        }

        /**
         * Set the indent to a given number of spaces.
         *
         * @param spaces the number of spaces per indent level (must be non-negative)
         * @return this builder
         */
        public Builder indent(final int spaces) {
            Assert.checkMinimumParameter("spaces", 0, spaces);
            this.indentStr = " ".repeat(spaces);
            return this;
        }

        /**
         * Set the maximum nesting depth. Nodes deeper than this limit are printed as
         * {@code {...}} without expanding their body.
         *
         * @param maxDepth the maximum depth (must be positive)
         * @return this builder
         */
        public Builder maxDepth(final int maxDepth) {
            Assert.checkMinimumParameter("maxDepth", 1, maxDepth);
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * Set whether to include class loader information in class descriptor output.
         *
         * @param showClassLoader {@code true} to include class loader lines, {@code false} to omit them
         * @return this builder
         */
        public Builder showClassLoader(final boolean showClassLoader) {
            this.showClassLoader = showClassLoader;
            return this;
        }

        /**
         * {@return a new {@link Printer} with the configured settings}
         */
        public Printer build() {
            return new Printer(indentStr, maxDepth, showClassLoader);
        }
    }

    // ---- Utilities ----

    /**
     * Convert a {@link ClassDesc} to a human-readable Java type name.
     *
     * @param desc the class descriptor (must not be {@code null})
     * @return the human-readable name (not {@code null})
     */
    static String typeName(final ClassDesc desc) {
        if (desc.isPrimitive()) {
            return switch (desc.descriptorString().charAt(0)) {
                case 'Z' -> "boolean";
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'S' -> "short";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'F' -> "float";
                case 'D' -> "double";
                case 'V' -> "void";
                default -> desc.descriptorString();
            };
        }
        if (desc.isArray()) {
            return typeName(desc.componentType()) + "[]";
        }
        String d = desc.descriptorString();
        return d.substring(1, d.length() - 1).replace('/', '.');
    }

    /**
     * Compute a human-readable array type name from an array class descriptor,
     * e.g. {@code "java.lang.Object[]"} or {@code "int[][]"}.
     *
     * @param arrayType the array class descriptor (must not be {@code null})
     * @return the type name with bracket suffixes (not {@code null})
     */
    static String arrayTypeName(final SerializedArrayClass arrayType) {
        return arrayType.leafComponentType().name() + "[]".repeat(arrayType.dimensions());
    }

    // ---- PrintContext (per-invocation mutable state) ----

    /**
     * Holds the mutable state for a single print operation: the output buffer,
     * visited-node tracking for circular references, and the current indent depth.
     */
    private final class PrintContext {
        /** The output buffer. */
        final StringBuilder sb = new StringBuilder();
        /** Maps visited nodes to their assigned labels for circular reference tracking. */
        private final IntMap<Serialized> visited = IntMap.identity();
        /** The next label number to assign. */
        private int nextLabel = 1;
        /** The current nesting depth for indentation. */
        private int depth = 0;

        // ---- Indentation helpers ----

        /**
         * Append the current indent prefix to the output.
         */
        private void indent() {
            for (int i = 0; i < depth; i++) {
                sb.append(indentStr);
            }
        }

        /**
         * Append a newline followed by the current indent.
         */
        private void newLine() {
            sb.append('\n');
            indent();
        }

        /**
         * Open a brace-delimited block: append {@code " {"} and increment depth.
         */
        private void openBlock() {
            sb.append(" {");
            depth++;
        }

        /**
         * Close a brace-delimited block: decrement depth, newline, append {@code "}"}.
         */
        private void closeBlock() {
            depth--;
            newLine();
            sb.append('}');
        }

        // ---- Circular reference tracking ----

        /**
         * Attempt to enter a compound node for printing. If the node has been visited
         * before, appends a back-reference ({@code <ref #N>}) and returns {@code false}.
         * If new, assigns a label, appends the label prefix ({@code #N }), and returns
         * {@code true}.
         *
         * @param node the node to visit
         * @return {@code true} if this is a new visit and the caller should print the body;
         *         {@code false} if a back-reference was printed
         */
        private boolean enter(final Serialized node) {
            if (visited.containsKey(node)) {
                sb.append("<ref #").append(visited.get(node)).append('>');
                return false;
            }
            int label = nextLabel++;
            visited.put(node, label);
            sb.append('#').append(label).append(' ');
            return true;
        }

        // ---- Main dispatch ----

        /**
         * Print any {@link Serialized} value, dispatching to the appropriate type-specific
         * method. Subclass checks are ordered before superclass checks where necessary.
         *
         * @param value the value to print (must not be {@code null})
         */
        private void printValue(final Serialized value) {
            // Leaf types (no cycle tracking needed)
            if (value instanceof SerializedNull) {
                sb.append("null");
            } else if (value instanceof SerializedString ss) {
                if (enter(ss)) {
                    printString(ss);
                }
            } else if (value instanceof SerializedKnownClassLoader cl) {
                printKnownClassLoader(cl);
            } else if (value instanceof SerializedPrimitiveClass pc) {
                sb.append(pc.name());

                // Class descriptors — subclasses before superclasses
            } else if (value instanceof SerializedSerializableClass sc) {
                printSerializableClass(sc);
            } else if (value instanceof SerializedRecordClass rc) {
                printRecordClass(rc);
            } else if (value instanceof SerializedEnumClass ec) {
                printEnumClass(ec);
            } else if (value instanceof SerializedExternalizableClass ec) {
                printExternalizableClass(ec);
            } else if (value instanceof SerializedArrayClass ac) {
                printArrayClass(ac);
            } else if (value instanceof SerializedSpecialSerializableClass ssc) {
                printSpecialSerializableClass(ssc);
            } else if (value instanceof SerializedNonSerializableClass nsc) {
                printNonSerializableClass(nsc);

                // Object instances
            } else if (value instanceof SerializedSerializable ss) {
                printSerializable(ss);
            } else if (value instanceof SerializedRecord sr) {
                printRecord(sr);
            } else if (value instanceof SerializedEnum se) {
                printEnum(se);
            } else if (value instanceof SerializedExternalizable se) {
                printExternalizable(se);
            } else if (value instanceof SerializedProxyObject po) {
                printProxyObject(po);
            } else if (value instanceof SerializedProxyClass pc) {
                printProxyClass(pc);

                // Arrays — object array before the abstract SerializedArray subtypes
            } else if (value instanceof SerializedObjectArray oa) {
                printObjectArray(oa);
            } else if (value instanceof SerializedBooleanArray ba) {
                printBooleanArray(ba);
            } else if (value instanceof SerializedByteArray ba) {
                printByteArray(ba);
            } else if (value instanceof SerializedCharArray ca) {
                printCharArray(ca);
            } else if (value instanceof SerializedShortArray sa) {
                printShortArray(sa);
            } else if (value instanceof SerializedIntArray ia) {
                printIntArray(ia);
            } else if (value instanceof SerializedLongArray la) {
                printLongArray(la);
            } else if (value instanceof SerializedFloatArray fa) {
                printFloatArray(fa);
            } else if (value instanceof SerializedDoubleArray da) {
                printDoubleArray(da);
            } else {
                sb.append("<unknown: ").append(value.getClass().getName()).append('>');
            }
        }

        // ---- Leaf type printers ----

        /**
         * Print a string value with double-quote delimiters and escape sequences.
         */
        private void printString(final SerializedString ss) {
            sb.append('"');
            appendEscapedString(ss.string());
            sb.append('"');
        }

        /**
         * Print a known class loader by its kind name.
         */
        private void printKnownClassLoader(final SerializedKnownClassLoader cl) {
            sb.append(switch (cl.kind()) {
                case BOOT -> "boot class loader";
                case PLATFORM -> "platform class loader";
                case APP -> "app class loader";
                case UNSPECIFIED -> "unspecified class loader";
            });
        }

        // ---- Class descriptor printers ----

        /**
         * Print a serializable class descriptor with UID, write-method flag, fields, and superclass chain.
         */
        private void printSerializableClass(final SerializedSerializableClass sc) {
            if (!enter(sc)) {
                return;
            }
            sb.append("serializable class ").append(sc.name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            printVersionedClassBody(sc);
            newLine();
            sb.append("hasWriteMethod: ").append(sc.hasWriteMethod());
            printFieldDeclarations(sc.streamFields());
            if (sc.superClass() != null) {
                newLine();
                sb.append("superClass: ");
                printValue(sc.superClass());
            }
            closeBlock();
        }

        /**
         * Print a record class descriptor with UID and fields.
         */
        private void printRecordClass(final SerializedRecordClass rc) {
            if (!enter(rc)) {
                return;
            }
            sb.append("record class ").append(rc.name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            printVersionedClassBody(rc);
            printFieldDeclarations(rc.streamFields());
            closeBlock();
        }

        /**
         * Print an enum class descriptor with UID.
         */
        private void printEnumClass(final SerializedEnumClass ec) {
            if (!enter(ec)) {
                return;
            }
            sb.append("enum class ").append(ec.name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            printVersionedClassBody(ec);
            closeBlock();
        }

        /**
         * Print an externalizable class descriptor with UID.
         */
        private void printExternalizableClass(final SerializedExternalizableClass ec) {
            if (!enter(ec)) {
                return;
            }
            sb.append("externalizable class ").append(ec.name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            printVersionedClassBody(ec);
            if (ec.superClass() != null) {
                newLine();
                sb.append("superClass: ");
                printValue(ec.superClass());
            }
            closeBlock();
        }

        /**
         * Print an array class descriptor with UID and component type.
         */
        private void printArrayClass(final SerializedArrayClass ac) {
            if (!enter(ac)) {
                return;
            }
            sb.append("array class ").append(ac.name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            printVersionedClassBody(ac);
            newLine();
            sb.append("componentType: ");
            printValue(ac.componentType());
            closeBlock();
        }

        /**
         * Print a special serializable class descriptor (String, Enum) with UID.
         */
        private void printSpecialSerializableClass(final SerializedSpecialSerializableClass ssc) {
            if (!enter(ssc)) {
                return;
            }
            sb.append("special class ").append(ssc.name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            printVersionedClassBody(ssc);
            closeBlock();
        }

        /**
         * Print a non-serializable class descriptor with class loader.
         */
        private void printNonSerializableClass(final SerializedNonSerializableClass nsc) {
            if (!enter(nsc)) {
                return;
            }
            sb.append("class ").append(nsc.name()).append(" (non-serializable)");
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            if (showClassLoader) {
                newLine();
                sb.append("classLoader: ");
                printValue(nsc.classLoader());
            }
            closeBlock();
        }

        /**
         * Print a proxy class descriptor with interface list and class loader.
         */
        private void printProxyClass(final SerializedProxyClass pc) {
            if (!enter(pc)) {
                return;
            }
            sb.append("proxy class ");
            appendInterfaceList(pc.interfaceNames());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            if (showClassLoader) {
                newLine();
                sb.append("classLoader: ");
                printValue(pc.classLoader());
            }
            if (pc.superClass() != null) {
                newLine();
                sb.append("superClass: ");
                printValue(pc.superClass());
            }
            closeBlock();
        }

        /**
         * Print the common body of a versioned class descriptor: serialVersionUID and class loader.
         */
        private void printVersionedClassBody(final SerializedVersionedClass vc) {
            newLine();
            sb.append("serialVersionUID: ").append(vc.serialVersionUID()).append('L');
            if (showClassLoader) {
                newLine();
                sb.append("classLoader: ");
                printValue(vc.classLoader());
            }
        }

        /**
         * Print a {@code fields { ... }} block listing the field declarations of a fielded class.
         * Does nothing if the field list is empty.
         */
        private void printFieldDeclarations(final List<SerialField> fields) {
            if (fields.isEmpty()) {
                return;
            }
            newLine();
            sb.append("fields");
            openBlock();
            for (SerialField field : fields) {
                newLine();
                sb.append(typeName(field.type())).append(' ').append(field.name());
            }
            closeBlock();
        }

        // ---- Object instance printers ----

        /**
         * Print a {@link SerializedSerializable} object instance with its class descriptor
         * and per-class-level field data. The data list is walked in parallel with the
         * class descriptor's {@code superClass()} chain. Extra data entries not matching
         * any level in the chain are flagged as unexpected.
         */
        private void printSerializable(final SerializedSerializable ss) {
            if (!enter(ss)) {
                return;
            }
            sb.append("serializable ").append(ss.serializedClass().name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            newLine();
            sb.append("class: ");
            printValue(ss.serializedClass());
            // walk the class hierarchy root-to-leaf alongside the data list
            List<SerializedSerializableClass> levels = new ArrayList<>();
            for (var c = ss.serializedClass(); c != null; c = c.superClass()) {
                levels.add(0, c);
            }
            ListIterator<SerialData> dataIter = ss.data().listIterator();
            for (var c : levels) {
                SerialData data = consumeIfMatches(dataIter, c);
                if (data == null) {
                    continue;
                }
                newLine();
                sb.append(c.name());
                openBlock();
                printFieldValues(data);
                printStreamDataList(data.streamData());
                closeBlock();
            }
            // flag any unexpected extra data entries
            while (dataIter.hasNext()) {
                SerialData extra = dataIter.next();
                newLine();
                sb.append("<unexpected data for ").append(extra.serializedClass().name()).append('>');
                openBlock();
                printFieldValues(extra);
                printStreamDataList(extra.streamData());
                closeBlock();
            }
            closeBlock();
        }

        /**
         * Consume the next data entry from the iterator if it matches the expected class descriptor.
         *
         * @param iter the data list iterator
         * @param expected the expected class descriptor for this level
         * @return the matching data entry, or {@code null} if no match
         */
        private static SerialData consumeIfMatches(final ListIterator<SerialData> iter,
                final SerializedSerializableClass expected) {
            if (iter.hasNext()) {
                SerialData entry = iter.next();
                if (entry.serializedClass() == expected) {
                    return entry;
                }
                iter.previous();
            }
            return null;
        }

        /**
         * Print a {@link SerializedRecord} instance with its class descriptor
         * and field values (single level).
         */
        private void printRecord(final SerializedRecord sr) {
            if (!enter(sr)) {
                return;
            }
            sb.append("record ").append(sr.recordClass().name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            newLine();
            sb.append("class: ");
            printValue(sr.recordClass());
            printFieldValues(sr.fieldData());
            closeBlock();
        }

        /**
         * Print a {@link SerializedEnum} instance with its class descriptor and constant name.
         */
        private void printEnum(final SerializedEnum se) {
            if (!enter(se)) {
                return;
            }
            sb.append("enum ").append(se.enumClass().name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            newLine();
            sb.append("class: ");
            printValue(se.enumClass());
            newLine();
            sb.append("constantName: ");
            printValue(se.constantName());
            closeBlock();
        }

        /**
         * Print a {@link SerializedExternalizable} instance with its class descriptor
         * and stream data.
         */
        private void printExternalizable(final SerializedExternalizable se) {
            if (!enter(se)) {
                return;
            }
            sb.append("externalizable ").append(se.serializedClass().name());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            newLine();
            sb.append("class: ");
            printValue(se.serializedClass());
            printStreamDataList(se.data());
            closeBlock();
        }

        /**
         * Print a {@link SerializedProxyObject} instance with its class descriptor
         * and invocation handler.
         */
        private void printProxyObject(final SerializedProxyObject po) {
            if (!enter(po)) {
                return;
            }
            sb.append("proxy ");
            appendInterfaceList(po.proxyClass().interfaceNames());
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            newLine();
            sb.append("class: ");
            printValue(po.proxyClass());
            newLine();
            sb.append("invocationHandler: ");
            printValue(po.invocationHandler());
            closeBlock();
        }

        // ---- Array printers ----

        /**
         * Print a {@link SerializedObjectArray} with indexed elements.
         */
        private void printObjectArray(final SerializedObjectArray oa) {
            if (!enter(oa)) {
                return;
            }
            SerializedArrayClass at = oa.arrayType();
            sb.append(at.leafComponentType().name());
            sb.append("[]".repeat(at.dimensions() - 1));
            sb.append('[').append(oa.length()).append(']');
            if (oa.length() == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            for (int i = 0; i < oa.length(); i++) {
                newLine();
                sb.append('[').append(i).append("] = ");
                printValue(oa.get(i));
            }
            closeBlock();
        }

        /**
         * Print a {@link SerializedBooleanArray} with one value per line.
         */
        private void printBooleanArray(final SerializedBooleanArray ba) {
            if (!enter(ba)) {
                return;
            }
            boolean[] arr = ba.asArray();
            sb.append("boolean[").append(arr.length).append(']');
            if (arr.length == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            for (boolean v : arr) {
                newLine();
                sb.append(v).append(',');
            }
            closeBlock();
        }

        /**
         * Print a {@link SerializedByteArray} as a grouped hex dump with offset and
         * Unicode control picture sidebar.
         */
        private void printByteArray(final SerializedByteArray ba) {
            if (!enter(ba)) {
                return;
            }
            byte[] arr = ba.asArray();
            sb.append("byte[").append(arr.length).append(']');
            if (arr.length == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            printByteHexDump(arr);
            closeBlock();
        }

        /**
         * Print a {@link SerializedCharArray} as a 4-digit hex dump with one group of 8
         * per line and a Unicode sidebar.
         */
        private void printCharArray(final SerializedCharArray ca) {
            if (!enter(ca)) {
                return;
            }
            char[] arr = ca.asArray();
            sb.append("char[").append(arr.length).append(']');
            if (arr.length == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            printCharHexDump(arr);
            closeBlock();
        }

        /**
         * Print a {@link SerializedShortArray} with one value per line.
         */
        private void printShortArray(final SerializedShortArray sa) {
            if (!enter(sa)) {
                return;
            }
            short[] arr = sa.asArray();
            sb.append("short[").append(arr.length).append(']');
            if (arr.length == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            for (short v : arr) {
                newLine();
                sb.append(v).append(',');
            }
            closeBlock();
        }

        /**
         * Print a {@link SerializedIntArray} with one value per line.
         */
        private void printIntArray(final SerializedIntArray ia) {
            if (!enter(ia)) {
                return;
            }
            int[] arr = ia.asArray();
            sb.append("int[").append(arr.length).append(']');
            if (arr.length == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            for (int v : arr) {
                newLine();
                sb.append(v).append(',');
            }
            closeBlock();
        }

        /**
         * Print a {@link SerializedLongArray} with one value per line, suffixed with {@code L}.
         */
        private void printLongArray(final SerializedLongArray la) {
            if (!enter(la)) {
                return;
            }
            long[] arr = la.asArray();
            sb.append("long[").append(arr.length).append(']');
            if (arr.length == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            for (long v : arr) {
                newLine();
                sb.append(v).append("L,");
            }
            closeBlock();
        }

        /**
         * Print a {@link SerializedFloatArray} with one value per line, suffixed with {@code f}.
         */
        private void printFloatArray(final SerializedFloatArray fa) {
            if (!enter(fa)) {
                return;
            }
            float[] arr = fa.asArray();
            sb.append("float[").append(arr.length).append(']');
            if (arr.length == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            for (float v : arr) {
                newLine();
                sb.append(v).append("f,");
            }
            closeBlock();
        }

        /**
         * Print a {@link SerializedDoubleArray} with one value per line, suffixed with {@code d}.
         */
        private void printDoubleArray(final SerializedDoubleArray da) {
            if (!enter(da)) {
                return;
            }
            double[] arr = da.asArray();
            sb.append("double[").append(arr.length).append(']');
            if (arr.length == 0) {
                sb.append(" {}");
                return;
            }
            if (depth >= maxDepth) {
                sb.append(" {...}");
                return;
            }
            openBlock();
            for (double v : arr) {
                newLine();
                sb.append(v).append("d,");
            }
            closeBlock();
        }

        // ---- Field value and stream data helpers ----

        /**
         * Print the field values from a {@link SerialData} entry, iterating the stream
         * field layout and reading primitive values from the byte buffer or object values
         * from the object buffer.
         */
        private void printFieldValues(final SerialData data) {
            for (SerialField field : data.serializedClass().streamFields()) {
                newLine();
                sb.append(typeName(field.type())).append(' ').append(field.name()).append(" = ");
                if (field.isPrimitive()) {
                    printPrimitiveFieldValue(data.primitiveFieldData(), field);
                } else {
                    printValue(data.objectFieldData().getObject(field.offset()));
                }
            }
        }

        /**
         * Print a primitive field value by reading from the byte buffer at the field's offset,
         * dispatching on the field's type code.
         */
        private void printPrimitiveFieldValue(final StreamData.OfBytes primData, final SerialField field) {
            int offset = field.offset();
            switch (field.typeCode()) {
                case 'Z' -> sb.append(primData.getBoolean(offset));
                case 'B' -> sb.append(primData.getByte(offset));
                case 'C' -> {
                    sb.append('\'');
                    appendEscapedChar(primData.getChar(offset));
                    sb.append('\'');
                }
                case 'S' -> sb.append(primData.getShort(offset));
                case 'I' -> sb.append(primData.getInt(offset));
                case 'J' -> sb.append(primData.getLong(offset)).append('L');
                case 'F' -> sb.append(primData.getFloat(offset)).append('f');
                case 'D' -> sb.append(primData.getDouble(offset)).append('d');
                default -> sb.append("???");
            }
        }

        /**
         * Print a list of {@link StreamData} entries as a {@code stream data { ... }} block.
         * Byte data is shown as a hex dump; object data as indexed nested values.
         * Does nothing if the list is empty.
         */
        private void printStreamDataList(final List<StreamData> streamData) {
            if (streamData.isEmpty()) {
                return;
            }
            newLine();
            sb.append("stream data");
            openBlock();
            for (StreamData sd : streamData) {
                if (sd instanceof StreamData.OfBytes ofb) {
                    newLine();
                    sb.append("bytes (").append(ofb.size()).append(" bytes)");
                    if (!ofb.isEmpty()) {
                        openBlock();
                        printByteHexDump(ofb.getBytes());
                        closeBlock();
                    }
                } else if (sd instanceof StreamData.OfObjects ofo) {
                    newLine();
                    sb.append("objects (").append(ofo.size()).append(')');
                    if (!ofo.isEmpty()) {
                        openBlock();
                        for (int i = 0; i < ofo.size(); i++) {
                            newLine();
                            sb.append('[').append(i).append("] = ");
                            printValue(ofo.getObject(i));
                        }
                        closeBlock();
                    }
                }
            }
            closeBlock();
        }

        // ---- Hex dump helpers ----

        /**
         * Print a byte array as a grouped hex dump with 8-digit offsets, two groups of 8 bytes
         * per line, and a sidebar using Unicode control pictures for control characters.
         */
        private void printByteHexDump(final byte[] bytes) {
            for (int off = 0; off < bytes.length; off += 16) {
                newLine();
                appendHex8(off);
                sb.append("  ");
                int lineEnd = Math.min(off + 16, bytes.length);
                for (int i = 0; i < 16; i++) {
                    if (i == 8) {
                        sb.append(' ');
                    }
                    if (off + i < lineEnd) {
                        appendHex2(bytes[off + i] & 0xFF);
                        sb.append(' ');
                    } else {
                        sb.append("   ");
                    }
                }
                sb.append(" |");
                for (int i = off; i < lineEnd; i++) {
                    sb.append(byteToSidebarChar(bytes[i] & 0xFF));
                }
                sb.append('|');
            }
        }

        /**
         * Print a char array as a 4-digit hex dump with 4-digit offsets (in character positions),
         * one group of 8 chars per line, and a Unicode sidebar using U+FFFD for non-printable
         * characters.
         */
        private void printCharHexDump(final char[] chars) {
            for (int off = 0; off < chars.length; off += 8) {
                newLine();
                appendHex4(off);
                sb.append("  ");
                int lineEnd = Math.min(off + 8, chars.length);
                for (int i = 0; i < 8; i++) {
                    if (off + i < lineEnd) {
                        appendHex4(chars[off + i]);
                    } else {
                        sb.append("    ");
                    }
                    sb.append(' ');
                }
                sb.append(" |");
                for (int i = off; i < lineEnd; i++) {
                    char c = chars[i];
                    sb.append(isPrintableChar(c) ? c : '�');
                }
                sb.append('|');
            }
        }

        /**
         * Append a 2-digit hex value to the output.
         */
        private void appendHex2(final int b) {
            sb.append(HEX[(b >> 4) & 0xF]);
            sb.append(HEX[b & 0xF]);
        }

        /**
         * Append a 4-digit hex value to the output.
         */
        private void appendHex4(final int v) {
            sb.append(HEX[(v >> 12) & 0xF]);
            sb.append(HEX[(v >> 8) & 0xF]);
            sb.append(HEX[(v >> 4) & 0xF]);
            sb.append(HEX[v & 0xF]);
        }

        /**
         * Append an 8-digit hex value to the output.
         */
        private void appendHex8(final int v) {
            appendHex4(v >>> 16);
            appendHex4(v & 0xFFFF);
        }

        /**
         * Convert a byte value (0x00–0xFF) to its sidebar display character.
         * Uses Unicode control pictures (U+2400–U+241F) for control characters 0x00–0x1F,
         * U+2421 (SYMBOL FOR DELETE) for 0x7F, the literal character for printable ASCII
         * (0x20–0x7E), and {@code '.'} for high bytes (0x80–0xFF).
         *
         * @param b the unsigned byte value
         * @return the sidebar character
         */
        private static char byteToSidebarChar(final int b) {
            if (b < 0x20) {
                return (char) (0x2400 + b);
            } else if (b < 0x7F) {
                return (char) b;
            } else if (b == 0x7F) {
                return '␡';
            } else {
                return '.';
            }
        }

        /**
         * {@return {@code true} if the character is considered printable for sidebar display}
         * A character is printable if it is defined, not an ISO control character, and not a
         * surrogate.
         *
         * @param c the character to test
         */
        private static boolean isPrintableChar(final char c) {
            return !Character.isISOControl(c) && Character.isDefined(c) && !Character.isSurrogate(c);
        }

        // ---- String escaping helpers ----

        /**
         * Append a string to the output with Java-style escape sequences for special characters.
         */
        private void appendEscapedString(final String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '\0' -> sb.append("\\0");
                    case '\t' -> sb.append("\\t");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\\' -> sb.append("\\\\");
                    case '"' -> sb.append("\\\"");
                    default -> {
                        if (Character.isISOControl(c)) {
                            sb.append("\\u");
                            appendHex4(c);
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
        }

        /**
         * Append a single character to the output with Java-style escape sequences,
         * suitable for use inside single-quote char literals.
         */
        private void appendEscapedChar(final char c) {
            switch (c) {
                case '\0' -> sb.append("\\0");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                default -> {
                    if (Character.isISOControl(c)) {
                        sb.append("\\u");
                        appendHex4(c);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }

        /**
         * Append a bracketed, comma-separated list of interface names.
         */
        private void appendInterfaceList(final List<String> interfaceNames) {
            sb.append('[');
            for (int i = 0; i < interfaceNames.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(interfaceNames.get(i));
            }
            sb.append(']');
        }
    }
}
