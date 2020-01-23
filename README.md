xor-filter
==========

[![CircleCI](https://circleci.com/gh/komiya-atsushi/xor-filter/tree/develop.svg?style=svg)](https://circleci.com/gh/komiya-atsushi/xor-filter/tree/develop)
[ ![Download](https://api.bintray.com/packages/komiya-atsushi/maven/xor-filter/images/download.svg) ](https://bintray.com/komiya-atsushi/maven/xor-filter/_latestVersion)

Yet another Java implementation of the [Xor Filters](https://arxiv.org/abs/1912.08258).


How to use
----------

```gradle
plugins {
    id 'java'
}

sourceCompatibility = 11

repositories {
    mavenCentral()
    maven {
        url "https://dl.bintray.com/komiya-atsushi/maven"
    }
}

dependencies {
    implementation 'com.google.guava:guava:28.2-jre'
    implementation 'me.k11i:xor-filter:0.1.1'
}
```

```java
package me.k11i.demo;

import com.google.common.hash.Funnels;
import me.k11i.xorfilter.XorFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class XorFilterDemo {
    public static void main(String[] args) throws IOException {
        List<String> strings = List.of(
                "1235df54-e42e-457b-9d9d-de0c46ecdfe7",
                "4ea534e3-6552-4975-b520-196ac5124423",
                "8b0fbde6-aaab-4a53-a546-be4cccdae3e9",
                "82570f67-7378-4772-ada2-ac7f1b44dbcf"
        );

        // Build Xor filter from the collection.
        XorFilter<String> filter = XorFilter.build(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                strings,
                XorFilter.Strategy.MURMUR128_XOR8);

        // Query whether the filter contains the element.
        boolean result = filter.mightContain(strings.get(0));
        System.out.println(result);  // => true

        // XorFilter implements java.util.function.Predicate interface.
        result = filter.test("8d05d410-1a0c-467b-9554-89c453a5");
        System.out.println(result);  // => false

        // Serialize the filter.
        byte[] serialized;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            filter.writeTo(out);
            serialized = out.toByteArray();
        }

        // Deserialize and use the filter.
        XorFilter<String> deserialized = XorFilter.readFrom(
                new ByteArrayInputStream(serialized),
                Funnels.stringFunnel(StandardCharsets.UTF_8));

        System.out.println(filter.mightContain(strings.get(0)));  // => true
        System.out.println(filter.mightContain("8d05d410-1a0c-467b-9554-89c453a5"));  // => false
    }
}
```

## Benchmark

### Throughput (queries/ms)

| Type of the element | Algorithm | Queries per ms |
| :----- | :-------------------------- | ---------: |
| Long   | Bloom filter (fpp = 0.389%) |  8,879.024 |
|        | Bloom filter (fpp = 0.002%) |  6,534.761 |
|        | Xor filter (8 bit)          | 17,458.015 |
|        | Xor filter (16 bit)         | 16,975.909 |
| Srring | Bloom filter (fpp = 0.389%) |  5,318.409 |
|        | Bloom filter (fpp = 0.002%) |  4,449.762 |
|        | Xor filter (8 bit)          |  7,121.787 |
|        | Xor filter (16 bit)         |  7,128.727 |

### Serialization size

These serialization sizes of the filters approximately equal to the memory footprint of the filters.

| Algorithm | Serialization size (byte) | Bit per entry |
| --- | ---: | ---: |
| Bloom filter (fpp = 0.389%) | 144,390 | 11.551 |
| Bloom filter (fpp = 0.002%) | 281,510 | 22.521 |
| Xor filter (8 bit) | 123,042 | 9.843 |
| Xor filter (16 bit) | 246,075 | 19.686 |