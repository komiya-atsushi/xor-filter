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
    implementation 'me.k11i:xor-filter:0.1.0'
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

```
Benchmark                                            (filterFactory)   Mode  Cnt      Score      Error   Units
QueryBenchmark.QueryLongBenchmark.benchmark    BLOOM_FILTER_FPP00389  thrpt   25   8879.024 ±   33.261  ops/ms
QueryBenchmark.QueryLongBenchmark.benchmark    BLOOM_FILTER_FPP00002  thrpt   25   6534.761 ±  124.038  ops/ms
QueryBenchmark.QueryLongBenchmark.benchmark                    XOR_8  thrpt   25  17458.015 ±  905.750  ops/ms
QueryBenchmark.QueryLongBenchmark.benchmark                   XOR_16  thrpt   25  16975.909 ± 1041.227  ops/ms
QueryBenchmark.QueryStringBenchmark.benchmark  BLOOM_FILTER_FPP00389  thrpt   25   5318.409 ±  118.598  ops/ms
QueryBenchmark.QueryStringBenchmark.benchmark  BLOOM_FILTER_FPP00002  thrpt   25   4449.762 ±   85.742  ops/ms
QueryBenchmark.QueryStringBenchmark.benchmark                  XOR_8  thrpt   25   7121.787 ±   58.167  ops/ms
QueryBenchmark.QueryStringBenchmark.benchmark                 XOR_16  thrpt   25   7128.727 ±  104.235  ops/ms
```
