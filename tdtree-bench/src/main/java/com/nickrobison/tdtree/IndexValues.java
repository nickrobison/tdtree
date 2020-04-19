package com.nickrobison.tdtree;

import java.util.Random;

/**
 * Created by nrobison on 5/3/17.
 */
class IndexValues {
    private final String key;
    private final long start;
    private final long end;

    IndexValues(long key, long start, long end) {
        this.key = Long.toString(key);
        this.start = start;
        this.end = end;
    }

    String getKey() {
        return key;
    }

    long getStart() {
        return start;
    }

    long getEnd() {
        return end;
    }

    static IndexValues[] generateTestRecords(int seed, int limit) {
        final Random r = new Random(seed);
        return r.longs(limit, 0, TDTree.maxValue)
                .mapToObj(key -> {
                    long end = key + r.nextInt(50000);
                    if (end > TDTree.maxValue) {
                        return new IndexValues(key, key, TDTree.maxValue);
                    } else {
                        return new IndexValues(key, key, end);
                    }
                })
                .toArray(IndexValues[]::new);
    }
}
