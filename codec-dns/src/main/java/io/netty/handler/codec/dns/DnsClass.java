/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import java.util.Arrays;

/**
 * DNS record resource class, with defaults for RFC-defined classes.
 */
public class DnsClass implements Comparable<DnsClass> {

    public static final short CLASS_IN = 0x0001;
    public static final short CLASS_CSNET = 0x0002;
    public static final short CLASS_CHAOS = 0x0003;
    public static final short CLASS_HESIOD = 0x0004;
    public static final short CLASS_NONE = 0x00fe;
    public static final short CLASS_ANY = 0x00ff;
    public static final DnsClass IN = new DnsClass(CLASS_IN, "IN");
    public static final DnsClass CSNET = new DnsClass(CLASS_CSNET, "CSNET");
    public static final DnsClass CHAOS = new DnsClass(CLASS_CHAOS, "CHAOS");
    public static final DnsClass HESIOD = new DnsClass(CLASS_HESIOD, "HESIOD");
    public static final DnsClass NONE = new DnsClass(CLASS_NONE, "NONE");
    public static final DnsClass ANY = new DnsClass(CLASS_ANY, "ANY");

    private final short value;
    private static DnsClass[] values = new DnsClass[]{IN, CSNET, CHAOS, HESIOD, NONE, ANY};
    private final String name;

    public DnsClass(short value, String name) {
        this.value = value;
        this.name = name;
    }

    public final int intValue() {
        return value & 0xFFFF;
    }

    public final short shortValue() {
        return value;
    }

    /**
     * Register a new DnsClass. Unlikely to be used unless you are doing
     * something very unusual (i.e. using DNS protocol to serve some other
     * need). It is expected that, if this is called at all, it will be called
     * during application initialization.
     *
     * @param nue The new dns class
     * @throws IllegalArgumentException if the name or int value of the passed
     * DnsClass matches one already registered.
     */
    public static void register(DnsClass nue) {
        for (DnsClass clazz : values) {
            if (clazz.intValue() == nue.intValue()) {
                throw new IllegalArgumentException("Attempt to redefine " + clazz + " int value " + nue.intValue());
            }
            if (clazz.name().equals(nue.name())) {
                if (clazz.intValue() == nue.intValue()) {
                    throw new IllegalArgumentException("Attempt to redefine " + clazz + " namee " + nue.name());
                }
            }
        }
        DnsClass[] result = new DnsClass[values.length + 1];
        System.arraycopy(values, 0, result, 0, values.length);
        result[result.length - 1] = nue;
        Arrays.sort(values);
    }

    public static DnsClass[] values() {
        DnsClass[] result = new DnsClass[values.length];
        System.arraycopy(values, 0, result, 0, values.length);
        return result;
    }

    public final String name() {
        return name;
    }

    public String toString() {
        return name();
    }

    /**
     * Look up a DnsClass without throwing an exception if no constant is found.
     *
     * @param value The integer value
     * @return a DnsClass or null
     */
    public static DnsClass valueOf(int value) {
        if (value == CLASS_IN) { // common case
            return IN;
        }
        for (DnsClass dc : values()) {
            if (dc.intValue() == value) {
                return dc;
            }
        }
        return new DnsClass((short) value, "UNKNOWN(" + value + ")");
    }

    /**
     * Look up a DnsClass by name.
     *
     * @param name The name
     * @return A DnsClass or null.
     */
    public static DnsClass valueOf(CharSequence name) {
        for (DnsClass dc : values) {
            if (dc.name().contentEquals(name)) {
                return dc;
            }
        }
        return null;
    }

    @Override
    public int compareTo(DnsClass o) {
        int mine = intValue();
        int theirs = o.intValue();
        return mine == theirs ? 0 : mine > theirs ? 1 : -1;
    }

    public int hashCode() {
        return intValue() * 5153;
    }

    public boolean equals(Object o) {
        return o instanceof DnsClass && ((DnsClass) o).intValue() == intValue();
    }
}
