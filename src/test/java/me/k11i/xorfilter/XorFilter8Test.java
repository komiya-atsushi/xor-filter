package me.k11i.xorfilter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XorFilter8Test {
    @Test
    void testMightContainShouldReturnTrue() {
        final int numEntries = 10000;
        List<String> values = IntStream.range(0, numEntries)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());

        XorFilter8<String> filter = XorFilter8.buildFromStrings(values, 1);

        for (String value : values) {
            assertTrue(filter.mightContain(value));
        }
    }

    @Test
    void testMightContainShouldNotReturnTrue() {
        final int numEntries = 10000;
        List<String> values = IntStream.range(0, numEntries)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());

        XorFilter8<String> filter = XorFilter8.buildFromStrings(values, 1);

        final double expectedMaxFalsePositiveRate = 0.01;

        long falsePositiveCount = IntStream.range(numEntries, numEntries * 2)
                .mapToObj(String::valueOf)
                .filter(filter::mightContain)
                .count();

        double falsePositiveRate = falsePositiveCount / (double) numEntries;
        System.out.printf("False positive rate: %f%n", falsePositiveRate);

        assertTrue(falsePositiveRate < expectedMaxFalsePositiveRate);
    }
}