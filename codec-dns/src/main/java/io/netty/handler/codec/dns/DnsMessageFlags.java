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

import io.netty.util.internal.UnstableApi;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Defines, reads and writes dns message header flags.
 */
@UnstableApi
public enum DnsMessageFlags {
    RECURSION_DESIRED(8), AUTHORITATIVE_ANSWER(10), TRUNCATED(9), RECURSION_AVAILABLE(7), IS_REPLY(15);
    final byte flagsBitOffset;

    DnsMessageFlags(int flagsBitOffset) {
        this.flagsBitOffset = (byte) flagsBitOffset;
    }

    /**
     * The bit position within the DNS flags header octet pair for this flag.
     *
     * @return the offset
     */
    public int bitOffset() {
        return flagsBitOffset;
    }

    /**
     * Read the value of this flag from a number.
     *
     * @param flags the input value
     * @return true if it is set
     */
    public boolean read(short flags) {
        return (flags >> flagsBitOffset & 1) == 1;
    }

    /**
     * Write the value of this flag to the passed number, returning the result.
     *
     * @param flags
     * @param on the input value
     * @return the result
     */
    public short write(short flags, boolean on) {
        return flip(flags, flagsBitOffset, on);
    }

    /**
     * Set the bit position for this flag in the passed number and return it.
     *
     * @param flags The flags
     * @return The updated flags value
     */
    public short set(short flags) {
        return write(flags, true);
    }

    /**
     * Unset the bit position for this flag in the passed number and return it.
     *
     * @param flags
     * @return
     */
    public short unset(short flags) {
        return write(flags, false);
    }

    public static FlagSet forFlags(short flags) {
        short mask = 0;
        for (DnsMessageFlags flag : values()) {
            mask = flag.set(mask);
        }
        flags = (short) (flags & mask);
        return new WritableFlagSet(flags);
    }

    public static int read(Set<DnsMessageFlags> options) {
        return options instanceof FlagSet ? ((FlagSet) options).value()
                : new ReadOnlyFlagSet(options).value();
    }

    private static short flip(short value, int index, boolean on) {
        int i = 1 << index;
        if (on) {
            value |= i;
        } else {
            value &= i ^ 0xFFFF;
        }
        return value;
    }

    /**
     * Create a lightweight Set from these flags.
     *
     * @param readOnly If true, the result will be immutable.
     * @param flags The flags
     * @return
     */
    public static FlagSet setOf(boolean readOnly, DnsMessageFlags... flags) {
        return readOnly ? new ReadOnlyFlagSet(flags) : new WritableFlagSet(flags);
    }

    /**
     * Create a writable copy of a flag set.
     *
     * @param flags DNS flags
     * @return A set
     */
    public static FlagSet writableCopyOf(Set<DnsMessageFlags> flags) {
        return new WritableFlagSet(flags);
    }

    /**
     * Very low memory-consumption set implementation for flags, costing only
     * the 8 byte object header and 2 bytes for the stored bitmask.
     */
    public interface FlagSet extends Set<DnsMessageFlags> {

        /**
         * Get the bitmask of flags.
         *
         * @return The bitmask
         */
        short value();

        /**
         * Get a read-only view of this flag set
         *
         * @return A read only view
         */
        FlagSet unmodifiableView();

        /**
         * Logical or this flag set with the passed value, setting the
         * corresponding bits.
         *
         * @param value An input value
         * @return The result of the logical or
         */
        short or(short value);
    }

    private static class ReadOnlyFlagSet implements FlagSet {

        short value;
        static final DnsMessageFlags[] FLAGS = DnsMessageFlags.values();
        static final int[] BIT_POSITIONS = new int[FLAGS.length];

        static {
            for (int i = 0; i < FLAGS.length; i++) {
                BIT_POSITIONS[i] = FLAGS[i].bitOffset();
            }
        }

        ReadOnlyFlagSet(short value) {
            this.value =  value;
        }

        ReadOnlyFlagSet(DnsMessageFlags... flags) {
            for (DnsMessageFlags flag : flags) {
                value =  flag.write(value, true);
            }
        }

        ReadOnlyFlagSet(Set<DnsMessageFlags> flags) {
            addAll(flags);
        }

        @Override
        public short or(short value) {
            return (short) (this.value | value);
        }

        public short value() {
            return value;
        }

        @Override
        public int size() {
            return Integer.bitCount(value & 0xFFFF);
        }

        @Override
        public boolean isEmpty() {
            return value == 0;
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof DnsMessageFlags)) {
                return false;
            }
            DnsMessageFlags flag = (DnsMessageFlags) o;
            return flag.read(value());
        }

        @Override
        public Iterator<DnsMessageFlags> iterator() {
            return new Iterator<DnsMessageFlags>() {
                int pos = -1;

                @Override
                public boolean hasNext() {
                    if (pos >= FLAGS.length) {
                        return false;
                    }
                    while (pos++ < FLAGS.length - 1) {
                        if (FLAGS[pos].read(value())) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public DnsMessageFlags next() {
                    return FLAGS[pos];
                }
            };
        }

        @Override
        public Object[] toArray() {
            Object[] result = new Object[size()];
            int ix = 0;
            for (DnsMessageFlags flag : FLAGS) {
                if (flag.read(value)) {
                    result[ix++] = flag;
                }
            }
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            int size = size();
            if (a.length != size) {
                a = Arrays.copyOf(a, size);
            }
            int ix = 0;
            for (DnsMessageFlags flag : FLAGS) {
                if (flag.read(value)) {
                    a[ix++] = (T) flag;
                }
            }
            return a;
        }

        @Override
        public FlagSet unmodifiableView() {
            return this;
        }

        @Override
        public boolean add(DnsMessageFlags e) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public boolean addAll(Collection<? extends DnsMessageFlags> c) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof FlagSet) {
                return ((FlagSet) o).value() == value;
            } else if (o instanceof Set<?>) {
                Set<?> s = (Set<?>) o;
                if (s.size() == size()) {
                    return containsAll(s);
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            int h = 0;
            Iterator<DnsMessageFlags> i = iterator();
            while (i.hasNext()) {
                DnsMessageFlags obj = i.next();
                if (obj != null) {
                    h += obj.hashCode();
                }
            }
            return h;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (DnsMessageFlags f : this) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(f.name());
            }
            return sb.toString();
        }
    }

    private static class WritableFlagSet extends ReadOnlyFlagSet {

        WritableFlagSet(short value) {
            super(value);
        }

        WritableFlagSet(DnsMessageFlags... flags) {
            super(flags);
        }

        WritableFlagSet(Set<DnsMessageFlags> flags) {
            super(flags);
        }

        @Override
        public boolean add(DnsMessageFlags e) {
            boolean old = e.read(value);
            value = e.write(value, true);
            return !old;
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof DnsMessageFlags)) {
                return false;
            }
            DnsMessageFlags flag = (DnsMessageFlags) o;
            boolean old = flag.read(value);
            if (old) {
                value =  flag.write(value, false);
            }
            return old;
        }

        @Override
        @SuppressWarnings("element-type-mismatch")
        public boolean containsAll(Collection<?> c) {
            boolean result = true;
            for (Object o : c) {
                result = contains(o);
                if (!result) {
                    break;
                }
            }
            return result;
        }

        @Override
        public boolean addAll(Collection<? extends DnsMessageFlags> c) {
            int old = value;
            if (c instanceof FlagSet) {
                value = (short) (value | ((FlagSet) c).value());
            } else {
                for (DnsMessageFlags flag : c) {
                    value = flag.set(value);
                }
            }
            return value != old;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            short old = value;
            if (c instanceof FlagSet) {
                value = (short) (value & ((FlagSet) c).value());
            } else {
                for (DnsMessageFlags f : this) {
                    if (!c.contains(f)) {
                        value =  f.unset(value);
                    }
                }
            }
            return value != old;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            short old = value;
            if (c instanceof FlagSet) {
                value =  (short) (value ^ ((FlagSet) c).value());
            } else {
                for (Object flag : c) {
                    if (flag instanceof DnsMessageFlags) {
                        value =  ((DnsMessageFlags) flag).set(value);
                    }
                }
            }
            return value != old;
        }

        @Override
        public void clear() {
            value = 0;
        }

        @Override
        public DnsMessageFlags.FlagSet unmodifiableView() {
            return new ReadOnlyFlagSet(value);
        }
    }
}
