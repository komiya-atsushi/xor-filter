package me.k11i.xorfilter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import me.k11i.xorfilter.QueryBenchmark.FilterFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public class FppAndSerializationSize {
    public static void main(String[] args) {
        for (FilterFactory filterFactory : FilterFactory.values()) {
            test(filterFactory);
        }
    }

    static void test(FilterFactory filterFactory) {
        final int numElements = 100_000;
        final int numQueries = numElements * 100;

        Predicate<Long> filter = filterFactory.buildFilter(
                Funnels.longFunnel(),
                LongStream.range(0, numElements)
                        .boxed()
                        .collect(Collectors.toList()));

        long falsePositiveCount = LongStream.range(numElements, numElements + numQueries)
                .filter(filter::test)
                .count();

        int serializedSize;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (filter instanceof BloomFilter) {
                ((BloomFilter) filter).writeTo(out);
            } else {
                ((XorFilter) filter).writeTo(out);
            }
            serializedSize = out.size();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String line = Stream.of(
                filterFactory,
                falsePositiveCount,
                100.0 * falsePositiveCount / numQueries,
                serializedSize,
                8.0 * serializedSize / numElements)
                .map(Object::toString)
                .collect(Collectors.joining("\t"));
        System.out.println(line);
    }
}
