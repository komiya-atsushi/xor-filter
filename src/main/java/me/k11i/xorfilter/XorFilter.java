package me.k11i.xorfilter;

import com.google.common.hash.Funnel;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

@SuppressWarnings("UnstableApiUsage")
public class XorFilter<T> implements Predicate<T> {
    public enum Strategy {
        MURMUR128_XOR8 {
            @Override
            HashFunction newHashFunction(int seed) {
                return Hashing.murmur3_128(seed);
            }

            @Override
            KBitValueArray newArray(int capacity) {
                return new KBitValueArray._8(capacity);
            }
        },

        MURMUR128_XOR16 {
            @Override
            HashFunction newHashFunction(int seed) {
                return Hashing.murmur3_128(seed);
            }

            @Override
            KBitValueArray newArray(int capacity) {
                return new KBitValueArray._16(capacity);
            }
        };

        abstract HashFunction newHashFunction(int seed);

        abstract KBitValueArray newArray(int capacity);
    }

    private static abstract class KBitValueArray {
        static class _8 extends KBitValueArray {
            private final byte[] b;

            _8(int capacity) {
                super(capacity);
                b = new byte[capacity];
            }

            private byte fingerprint(long x) {
                return (byte) (x >>> 56);
            }

            @Override
            public void put(int index, long x, int h0, int h1, int h2) {
                b[index] = 0;
                b[index] = (byte) (fingerprint(x) ^ b[h0] ^ b[h1] ^ b[h2]);
            }

            @Override
            boolean contains(long x, int h0, int h1, int h2) {
                return fingerprint(x) == (b[h0] ^ b[h1] ^ b[h2]);
            }

            @Override
            void writeTo(DataOutputStream out) throws IOException {
                for (byte value : b) {
                    out.writeByte(value);
                }
            }

            @Override
            void readFrom(DataInputStream in) throws IOException {
                for (int i = 0; i < b.length; i++) {
                    b[i] = in.readByte();
                }
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                _8 that = (_8) o;
                return Arrays.equals(b, that.b);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(b);
            }
        }

        static class _16 extends KBitValueArray {
            private final short[] b;

            _16(int capacity) {
                super(capacity);
                b = new short[capacity];
            }

            private short fingerprint(long x) {
                return (short) (x >>> 48);
            }

            @Override
            void put(int index, long x, int h0, int h1, int h2) {
                b[index] = 0;
                b[index] = (short) (fingerprint(x) ^ b[h0] ^ b[h1] ^ b[h2]);
            }

            @Override
            boolean contains(long x, int h0, int h1, int h2) {
                return fingerprint(x) == (b[h0] ^ b[h1] ^ b[h2]);
            }

            @Override
            void writeTo(DataOutputStream out) throws IOException {
                for (short value : b) {
                    out.writeShort(value);
                }
            }

            @Override
            void readFrom(DataInputStream in) throws IOException {
                for (int i = 0; i < b.length; i++) {
                    b[i] = in.readShort();
                }
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                _16 that = (_16) o;
                return Arrays.equals(b, that.b);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(b);
            }
        }

        final int capacity;
        private final int blockLength;

        KBitValueArray(int capacity) {
            this.capacity = capacity;
            this.blockLength = capacity / 3;
        }

        void put(int index, long x) {
            long x2 = MurmurHashFinalizer.hash(0, x);
            put(index, x, h0(x, blockLength), h1(x2, blockLength), h2(x2, blockLength));
        }

        boolean contains(long x) {
            long x2 = MurmurHashFinalizer.hash(0, x);
            return contains(x, h0(x, blockLength), h1(x2, blockLength), h2(x2, blockLength));
        }

        abstract void put(int index, long x, int h0, int h1, int h2);

        abstract boolean contains(long x, int h0, int h1, int h2);

        abstract void writeTo(DataOutputStream out) throws IOException;

        abstract void readFrom(DataInputStream in) throws IOException;

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" +
                    "capacity=" + capacity +
                    '}';
        }
    }

    // ---

    public static <T> XorFilter<T> build(Funnel<? super T> funnel, Collection<T> elements, Strategy strategy) {
        return build(funnel, elements, strategy, ThreadLocalRandom.current().nextInt());
    }

    public static <T> XorFilter<T> build(Funnel<? super T> funnel, Collection<T> elements, Strategy strategy, int rngSeed) {
        final int capacity = (int) ((1.23 * elements.size() + 32 + 2) / 3) * 3;
        Mapping mapping = buildMapping(funnel, elements, strategy, capacity, rngSeed);
        KBitValueArray b = strategy.newArray(capacity);
        assign(b, mapping.stack);
        return new XorFilter<>(strategy, mapping.seed, funnel, b);
    }

    private static <T> Mapping buildMapping(
            Funnel<? super T> funnel,
            Collection<T> elements,
            Strategy strategy,
            int capacity,
            int rngSeed) {

        int blockLength = capacity / 3;
        HashedElements hashedElements = new HashedElements(elements.size());
        HashedElementSets h = new HashedElementSets(capacity);
        IntQueue q = new IntQueue(elements.size());
        PairStack s = new PairStack(elements.size());
        SplittableRandom r = new SplittableRandom(rngSeed);

        do {
            int seed = r.nextInt();
            HashFunction hashFunction = strategy.newHashFunction(seed);
            hashedElements.hashAll(o -> hashFunction.hashObject(o, funnel).asLong(), elements);

            for (int i = 0; i < hashedElements.size(); i++) {
                long x = hashedElements.get(i);
                long x2 = MurmurHashFinalizer.hash(0, x);

                h.append(h0(x, blockLength), x);
                h.append(h1(x2, blockLength), x);
                h.append(h2(x2, blockLength), x);
            }

            for (int i = 0; i < capacity; i++) {
                if (h.containsOnlyOneValue(i)) {
                    q.enqueue(i);
                }
            }

            while (q.isNotEmpty()) {
                int i = q.dequeue();
                if (h.containsOnlyOneValue(i)) {
                    long x = h.getSoleValue(i);
                    long x2 = MurmurHashFinalizer.hash(0, x);
                    s.push(i, x);

                    h.remove(h0(x, blockLength), x, q::enqueue);
                    h.remove(h1(x2, blockLength), x, q::enqueue);
                    h.remove(h2(x2, blockLength), x, q::enqueue);
                }
            }

            if (s.size() == hashedElements.size()) {
                return new Mapping(seed, s);
            }

            h.clear();
            q.clear();
            s.clear();

        } while (true);
    }

    private static void assign(KBitValueArray b, PairStack s) {
        while (s.isNotEmpty()) {
            int index = s.peekIndex();
            long x = s.popHashedElements();
            b.put(index, x);
        }
    }

    private static int h0(long x, int blockLength) {
        return (int) ((x & 0xffffffffL) * blockLength >>> 32);
    }

    private static int h1(long x2, int blockLength) {
        return (int) (((x2 & 0xffffffffL) * blockLength >>> 32) + blockLength);
    }

    private static int h2(long x2, int blockLength) {
        return (int) (((x2 >>> 32) * blockLength >>> 32) + blockLength + blockLength);
    }

    // ---

    private final Strategy strategy;
    private final int seed;
    private final HashFunction hashFunction;
    private final Funnel<? super T> funnel;
    private final KBitValueArray b;

    private XorFilter(Strategy strategy, int seed, Funnel<? super T> funnel, KBitValueArray b) {
        this.strategy = strategy;
        this.seed = seed;
        this.hashFunction = strategy.newHashFunction(seed);
        this.funnel = funnel;
        this.b = b;
    }

    public boolean mightContain(T element) {
        long x = hashFunction.hashObject(element, funnel).asLong();
        return b.contains(x);
    }

    @Override
    public boolean test(T t) {
        return mightContain(t);
    }

    public void writeTo(OutputStream out) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(out)) {
            dos.writeByte(strategy.ordinal());
            dos.writeInt(seed);
            dos.writeInt(b.capacity);
            b.writeTo(dos);
        }
    }

    public static <T> XorFilter<T> readFrom(InputStream in, Funnel<? super T> funnel) throws IOException {
        try (DataInputStream dis = new DataInputStream(in)) {
            Strategy strategy = Strategy.values()[dis.readByte()];
            int seed = dis.readInt();
            int capacity = dis.readInt();
            KBitValueArray b = strategy.newArray(capacity);
            b.readFrom(dis);
            return new XorFilter<>(strategy, seed, funnel, b);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XorFilter<?> xorFilter = (XorFilter<?>) o;
        return seed == xorFilter.seed &&
                strategy == xorFilter.strategy &&
                Objects.equals(hashFunction, xorFilter.hashFunction) &&
                Objects.equals(funnel, xorFilter.funnel) &&
                Objects.equals(b, xorFilter.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategy, seed, hashFunction, funnel, b);
    }

    @Override
    public String toString() {
        return "XorFilter{" +
                "strategy=" + strategy +
                ", seed=" + seed +
                ", hashFunction=" + hashFunction +
                ", funnel=" + funnel +
                ", b=" + b +
                '}';
    }
}

class HashedElements {
    private final long[] hashedElements;
    private int actualSize;

    HashedElements(int size) {
        this.hashedElements = new long[size];
    }

    <T> void hashAll(ToLongFunction<T> hashFunction, Collection<T> elements) {
        int i0 = 0;
        for (T o : elements) {
            hashedElements[i0++] = hashFunction.applyAsLong(o);
        }

        Arrays.sort(hashedElements);

        int p = 0;
        for (int i1 = 1; i1 < hashedElements.length; i1++) {
            if (hashedElements[p] != hashedElements[i1]) {
                hashedElements[++p] = hashedElements[i1];
            }
        }

        actualSize = p + 1;
    }

    int size() {
        return actualSize;
    }

    long get(int index) {
        return hashedElements[index];
    }
}

class HashedElementSets {
    private final long[] xorValues;
    private final byte[] counts;

    HashedElementSets(int capacity) {
        this.xorValues = new long[capacity];
        this.counts = new byte[capacity];
    }

    void clear() {
        Arrays.fill(xorValues, 0);
        Arrays.fill(counts, (byte) 0);
    }

    void append(int index, long x) {
        xorValues[index] ^= x;
        counts[index]++;
    }

    void remove(int index, long x, IntConsumer consumer) {
        xorValues[index] ^= x;
        counts[index]--;
        if (counts[index] == 1) {
            consumer.accept(index);
        }
    }

    boolean containsOnlyOneValue(int index) {
        return counts[index] == 1;
    }

    long getSoleValue(int index) {
        return xorValues[index];
    }
}

class IntQueue {
    private final int[] queue;
    private int putIndex;
    private int takeIndex;

    IntQueue(int size) {
        queue = new int[size + 1];
    }

    boolean isNotEmpty() {
        return putIndex != takeIndex;
    }

    void clear() {
        putIndex = takeIndex = 0;
    }

    void enqueue(int value) {
        queue[putIndex++] = value;
        if (putIndex == queue.length) {
            putIndex = 0;
        }
    }

    int dequeue() {
        int result = queue[takeIndex++];
        if (takeIndex == queue.length) {
            takeIndex = 0;
        }
        return result;
    }
}

class PairStack {
    private final int[] indexes;
    private final long[] hashedElements;
    private int stackIndex;

    PairStack(int size) {
        this.indexes = new int[size];
        this.hashedElements = new long[size];
    }

    int size() {
        return stackIndex;
    }

    boolean isNotEmpty() {
        return stackIndex > 0;
    }

    void clear() {
        stackIndex = 0;
    }

    void push(int index, long x) {
        indexes[stackIndex] = index;
        hashedElements[stackIndex] = x;
        stackIndex++;
    }

    int peekIndex() {
        return indexes[stackIndex - 1];
    }

    long popHashedElements() {
        return hashedElements[--stackIndex];
    }
}

class Mapping {
    final int seed;
    final PairStack stack;

    Mapping(int seed, PairStack stack) {
        this.seed = seed;
        this.stack = stack;
    }
}
