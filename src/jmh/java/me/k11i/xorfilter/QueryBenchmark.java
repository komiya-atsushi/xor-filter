package me.k11i.xorfilter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

@SuppressWarnings({"UnstableApiUsage", "unused"})
public class QueryBenchmark {
    private static final int NUM_ELEMENTS = 1 << 14;
    private static final int INDEX_MOD_MASK = NUM_ELEMENTS - 1;

    public enum FilterFactory {
        BLOOM_FILTER_FPP00389 {
            @Override
            <T> Predicate<T> buildFilter(Funnel<? super T> funnel, List<T> elements) {
                BloomFilter<T> filter = BloomFilter.create(funnel, elements.size(), 0.00389);
                elements.forEach(filter::put);
                return filter;
            }
        },

        BLOOM_FILTER_FPP00002 {
            @Override
            <T> Predicate<T> buildFilter(Funnel<? super T> funnel, List<T> elements) {
                BloomFilter<T> filter = BloomFilter.create(funnel, elements.size(), 0.00002);
                elements.forEach(filter::put);
                return filter;
            }
        },

        XOR_8 {
            @Override
            <T> Predicate<T> buildFilter(Funnel<? super T> funnel, List<T> elements) {
                return XorFilter.build(funnel, elements, XorFilter.Strategy.MURMUR128_XOR8);
            }
        },

        XOR_16 {
            @Override
            <T> Predicate<T> buildFilter(Funnel<? super T> funnel, List<T> elements) {
                return XorFilter.build(funnel, elements, XorFilter.Strategy.MURMUR128_XOR16);
            }
        };

        abstract <T> Predicate<T> buildFilter(Funnel<? super T> funnel, List<T> elements);
    }

    @State(Scope.Benchmark)
    public static class QueryStringBenchmark {
        private static final Funnel<CharSequence> FUNNEL = Funnels.stringFunnel(StandardCharsets.ISO_8859_1);

        @Param(value = {
                "BLOOM_FILTER_FPP00389",
                "BLOOM_FILTER_FPP00002",
                "XOR_8",
                "XOR_16",
        })
        private FilterFactory filterFactory;

        private Predicate<String> filter;
        private String[] strings;
        private int index;

        @Setup
        public void setUp() {
            strings = LongStream.range(0, NUM_ELEMENTS)
                    .map(l -> l * 0x9e3779b97f4a7c15L)
                    .mapToObj(l -> String.format("%020d", l))
                    .toArray(String[]::new);

            filter = filterFactory.buildFilter(
                    FUNNEL,
                    IntStream.range(0, strings.length / 2)
                            .map(i -> i * 2)
                            .mapToObj(i -> strings[i])
                            .collect(Collectors.toList()));
        }

        @Benchmark
        public boolean benchmark() {
            String query = strings[index];
            index = (index + 1) & INDEX_MOD_MASK;
            return filter.test(query);
        }
    }

    @State(Scope.Benchmark)
    public static class QueryLongBenchmark {
        private static final Funnel<Long> FUNNEL = Funnels.longFunnel();

        @Param(value = {
                "BLOOM_FILTER_FPP00389",
                "BLOOM_FILTER_FPP00002",
                "XOR_8",
                "XOR_16",
        })
        private FilterFactory filterFactory;

        private Predicate<Long> filter;
        private Long[] longs;
        private int index;

        @Setup
        public void setUp() {
            longs = new SplittableRandom(0).longs()
                    .limit(NUM_ELEMENTS)
                    .boxed()
                    .toArray(Long[]::new);

            filter = filterFactory.buildFilter(
                    FUNNEL,
                    IntStream.range(0, longs.length / 2)
                            .map(i -> i * 2)
                            .mapToObj(i -> longs[i])
                            .collect(Collectors.toList()));
        }

        @Benchmark
        public boolean benchmark() {
            Long query = longs[index];
            index = (index + 1) & INDEX_MOD_MASK;
            return filter.test(query);
        }
    }
}
