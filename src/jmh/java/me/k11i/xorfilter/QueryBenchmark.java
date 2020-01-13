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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

@State(Scope.Benchmark)
@SuppressWarnings("UnstableApiUsage")
public class QueryBenchmark {
    private static final int EXPECTED_INSERTIONS = 1 << 14;
    private static final int INDEX_MOD_MASK = EXPECTED_INSERTIONS - 1;
    private static final Funnel<CharSequence> FUNNEL = Funnels.stringFunnel(StandardCharsets.ISO_8859_1);

    public enum FilterFactory {
        BLOOM_FILTER_FPP001 {
            @Override
            Predicate<String> buildFilter(List<String> strings) {
                BloomFilter<String> filter = BloomFilter.create(FUNNEL, EXPECTED_INSERTIONS, 0.01);
                strings.forEach(filter::put);
                return filter;
            }
        },

        BLOOM_FILTER_FPP0001 {
            @Override
            Predicate<String> buildFilter(List<String> strings) {
                BloomFilter<String> filter = BloomFilter.create(FUNNEL, EXPECTED_INSERTIONS, 0.001);
                strings.forEach(filter::put);
                return filter;
            }
        },

        XOR_8 {
            @Override
            Predicate<String> buildFilter(List<String> strings) {
                return XorFilter.build(FUNNEL, strings, XorFilter.Strategy.MURMUR128_XOR8);
            }
        },

        XOR_16 {
            @Override
            Predicate<String> buildFilter(List<String> strings) {
                return XorFilter.build(FUNNEL, strings, XorFilter.Strategy.MURMUR128_XOR16);
            }
        };

        abstract Predicate<String> buildFilter(List<String> strings);
    }

    @Param(value = {
            "BLOOM_FILTER_FPP001",
            "BLOOM_FILTER_FPP0001",
            "XOR_8",
            "XOR_16",
    })
    private FilterFactory filterFactory;

    private Predicate<String> filter;
    private String[] strings;
    private int index;

    @Setup
    public void setUp() {
        strings = LongStream.range(0, EXPECTED_INSERTIONS)
                .map(l -> l * 0x9e3779b97f4a7c15L)
                .mapToObj(l -> String.format("%020d", l))
                .toArray(String[]::new);

        filter = filterFactory.buildFilter(
                IntStream.range(0, strings.length / 2)
                        .map(i -> i * 2)
                        .mapToObj(i -> strings[i])
                        .collect(Collectors.toList()));
    }

    @Benchmark
    public boolean benchmarkQuery() {
        String query = strings[index];
        index = (index + 1) & INDEX_MOD_MASK;
        return filter.test(query);
    }
}
