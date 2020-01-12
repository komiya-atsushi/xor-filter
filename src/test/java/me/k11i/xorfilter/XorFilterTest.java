package me.k11i.xorfilter;

import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("UnstableApiUsage")
class XorFilterTest {
    private static final Funnel<CharSequence> FUNNEL = Funnels.stringFunnel(StandardCharsets.ISO_8859_1);

    static Stream<XorFilter.Strategy> strategies() {
        return Stream.of(XorFilter.Strategy.values());
    }

    @ParameterizedTest
    @MethodSource("strategies")
    void testMightContainShouldReturnTrue(XorFilter.Strategy strategy) {
        final int numEntries = 10000;
        List<String> elements = IntStream.range(0, numEntries)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());

        XorFilter<String> filter = XorFilter.build(
                FUNNEL,
                elements,
                strategy);

        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            assertTrue(
                    filter.mightContain(element),
                    String.format("[%d]: %s", i, element));
        }
    }

    @ParameterizedTest
    @MethodSource("strategies")
    void testMightContainShouldNotReturnTrue(XorFilter.Strategy strategy) {
        final int numEntries = 10000;
        List<String> elements = IntStream.range(0, numEntries)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());

        XorFilter<String> filter = XorFilter.build(
                FUNNEL,
                elements,
                strategy);

        final double expectedMaxFalsePositiveRate = 0.01;

        System.out.print("False positives:");
        long falsePositiveCount = IntStream.range(numEntries, numEntries * 2)
                .mapToObj(String::valueOf)
                .filter(filter::mightContain)
                .peek(s -> System.out.printf(" %s", s))
                .count();

        double falsePositiveRate = falsePositiveCount / (double) numEntries;
        System.out.printf("%n# of false positives: %d%n", falsePositiveCount);
        System.out.printf("False positive rate: %f%n", falsePositiveRate);

        assertTrue(falsePositiveRate < expectedMaxFalsePositiveRate);
    }

    @ParameterizedTest
    @MethodSource("strategies")
    void testSerialization(XorFilter.Strategy strategy) throws IOException {
        final int numEntries = 10000;
        List<String> elements = IntStream.range(0, numEntries)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());

        XorFilter<String> filter = XorFilter.build(
                FUNNEL,
                elements.stream().limit(numEntries / 2).collect(Collectors.toList()),
                strategy);

        XorFilter<String> deserialized;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filter.writeTo(out);
            deserialized = XorFilter.readFrom(new ByteArrayInputStream(out.toByteArray()), FUNNEL);
        }

        assertEquals(filter, deserialized);

        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            assertEquals(
                    filter.mightContain(element),
                    deserialized.mightContain(element),
                    String.format("[%d]: %s", i, element));
        }
    }
}
