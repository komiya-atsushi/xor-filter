package me.k11i.xorfilter;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.nio.charset.Charset;

public interface ValueHashFunction<T> {
    long hash(T value);

    static ValueHashFunction<String> stringHashFunction(int seed) {
        return stringHashFunction(seed, Charset.defaultCharset());
    }

    @SuppressWarnings("ALL")
    static ValueHashFunction<String> stringHashFunction(int seed, Charset cs) {
        return new ValueHashFunction<String>() {
            private final Charset charset = cs;
            private final HashFunction hashFunction = Hashing.murmur3_128(seed);

            @Override
            public long hash(String value) {
                return hashFunction.hashBytes(value.getBytes(cs)).asLong();
            }
        };
    }
}
