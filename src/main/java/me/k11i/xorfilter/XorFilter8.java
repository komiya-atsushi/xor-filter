package me.k11i.xorfilter;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SplittableRandom;
import java.util.concurrent.ThreadLocalRandom;

public class XorFilter8<T> {
    static class Fingerprint {
        private final long seed;

        Fingerprint(long seed) {
            this.seed = seed;
        }

        long calculate64(long x) {
            return MurmurHashFinalizer.hash(seed, x);
        }

        byte calculate8(long x) {
            return (byte) (calculate64(x) >>> 56);
        }
    }

    static class InternalHashFunctions {
        private final long seed0;
        private final long seed1;
        private final long seed2;

        private final int hash0Size;
        private final int hash1Size;
        private final int hash2Size;

        private final int hash1Base;
        private final int hash2Base;

        InternalHashFunctions(int capacity, SplittableRandom r) {
            hash0Size = capacity / 3;
            hash1Size = 2 * capacity / 3 - hash0Size;
            hash2Size = capacity - (hash0Size + hash1Size);

            hash1Base = hash0Size;
            hash2Base = hash0Size + hash1Size;

            seed0 = r.nextLong();
            seed1 = r.nextLong();
            seed2 = r.nextLong();
        }

        int h0(long x) {
            return hashWithBound(seed0, x, hash0Size);
        }

        int h1(long x) {
            return hashWithBound(seed1, x, hash1Size) + hash1Base;
        }

        int h2(long x) {
            return hashWithBound(seed2, x, hash2Size) + hash2Base;
        }

        int hashWithBound(long seed, long x, int bound) {
            return (int) ((MurmurHashFinalizer.hash(seed, x) >>> 1) % bound);
        }

        Iterator iterator(long x) {
            return new Iterator(x);
        }

        class Iterator {
            private final long x;
            private int counter;

            Iterator(long x) {
                this.x = x;
            }

            boolean hasNext() {
                return counter < 3;
            }

            int next() {
                switch (counter++) {
                    case 0:
                        return h0(x);
                    case 1:
                        return h1(x);
                    case 2:
                        return h2(x);
                    default:
                        throw new NoSuchElementException();
                }
            }
        }
    }

    static class H {
        private static final int SET_INITIAL_CAPACITY = 3;
        private final LongArraySet[] array;

        H(int capacity) {
            array = new LongArraySet[capacity];
        }

        int size() {
            return array.length;
        }

        void clear() {
            for (LongArraySet set : array) {
                if (set != null) {
                    set.clear();
                }
            }
        }

        void append(int index, long x) {
            LongArraySet set = array[index];
            if (set == null) {
                array[index] = set = new LongArraySet(SET_INITIAL_CAPACITY);
            }
            set.add(x);
        }

        void remove(int index, long x) {
            array[index].remove(x);
        }

        boolean containsOnlySingleKey(int index) {
            LongArraySet set = array[index];
            return set != null && set.size() == 1;
        }

        long getSoleValue(int index) {
            return array[index].iterator().nextLong();
        }
    }

    static class S {
        private final IntArrayList is;
        private final LongArrayList xs;

        S(int size) {
            is = new IntArrayList(size);
            xs = new LongArrayList(size);
        }

        int size() {
            return is.size();
        }

        boolean isEmpty() {
            return is.isEmpty();
        }

        void clear() {
            is.clear();
            xs.clear();
        }

        void push(int index, long x) {
            is.push(index);
            xs.push(x);
        }

        int popIndex() {
            return is.popInt();
        }

        long popX() {
            return xs.popLong();
        }
    }

    static class Mapping {
        private final InternalHashFunctions hashFunctions;
        private final S s;

        Mapping(InternalHashFunctions hashFunctions, S s) {
            this.hashFunctions = hashFunctions;
            this.s = s;
        }
    }

    // ---

    private final ValueHashFunction<T> valueHashFunction;
    private final Fingerprint fingerprint;
    private final InternalHashFunctions hashFunctions;
    private final byte[] b;

    public XorFilter8(Collection<T> values, ValueHashFunction<T> valueHashFunction, long seed) {
        if (1.23 * values.size() + 32 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Size of the values is too large: " + values.size());
        }

        SplittableRandom r = new SplittableRandom(seed);

        this.valueHashFunction = valueHashFunction;
        this.fingerprint = new Fingerprint(r.nextLong());

        LongSet hashedValues = new LongOpenHashSet(values.size());
        for (T value : values) {
            hashedValues.add(valueHashFunction.hash(value));
        }

        int capacity = (int) (1.23 * hashedValues.size() + 32);
        Mapping mapping = buildMapping(capacity, r, hashedValues);

        this.hashFunctions = mapping.hashFunctions;
        this.b = assign(capacity, mapping, fingerprint);
    }

    private static Mapping buildMapping(int capacity, SplittableRandom r, LongSet values) {
        H h = new H(capacity);
        IntArrayFIFOQueue q = new IntArrayFIFOQueue(values.size());
        S s = new S(values.size());

        do {
            InternalHashFunctions hashFunctions = new InternalHashFunctions(capacity, r);

            for (LongIterator it = values.iterator(); it.hasNext(); ) {
                long x = it.nextLong();
                h.append(hashFunctions.h0(x), x);
                h.append(hashFunctions.h1(x), x);
                h.append(hashFunctions.h2(x), x);
            }

            for (int i = 0; i < h.size(); i++) {
                if (h.containsOnlySingleKey(i)) {
                    q.enqueue(i);
                }
            }

            while (!q.isEmpty()) {
                int i = q.dequeueInt();
                if (h.containsOnlySingleKey(i)) {
                    long x = h.getSoleValue(i);
                    s.push(i, x);

                    for (InternalHashFunctions.Iterator it = hashFunctions.iterator(x); it.hasNext(); ) {
                        int hv = it.next();

                        h.remove(hv, x);
                        if (h.containsOnlySingleKey(hv)) {
                            q.enqueue(hv);
                        }
                    }
                }
            }

            if (s.size() == values.size()) {
                return new Mapping(hashFunctions, s);
            }

            h.clear();
            q.clear();
            s.clear();

        } while (true);
    }

    private static byte[] assign(int capacity, Mapping m, Fingerprint f) {
        S s = m.s;
        InternalHashFunctions hashFunctions = m.hashFunctions;

        byte[] b = new byte[capacity];
        while (!s.isEmpty()) {
            int index = s.popIndex();
            long x = s.popX();

            b[index] = 0;
            b[index] = (byte) (f.calculate8(x)
                    ^ b[hashFunctions.h0(x)]
                    ^ b[hashFunctions.h1(x)]
                    ^ b[hashFunctions.h2(x)]);
        }

        return b;
    }

    public boolean mightContain(T value) {
        long x = valueHashFunction.hash(value);
        int h0 = hashFunctions.h0(x);
        int h1 = hashFunctions.h1(x);
        int h2 = hashFunctions.h2(x);
        return fingerprint.calculate8(x) == (b[h0] ^ b[h1] ^ b[h2]);
    }

    public static XorFilter8<String> buildFromStrings(List<String> values) {
        return buildFromStrings(values, ThreadLocalRandom.current().nextLong());
    }

    public static XorFilter8<String> buildFromStrings(List<String> values, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        ValueHashFunction<String> hashFunction = ValueHashFunction.stringHashFunction(r.nextInt());
        return new XorFilter8<>(values, hashFunction, r.nextLong());
    }
}
