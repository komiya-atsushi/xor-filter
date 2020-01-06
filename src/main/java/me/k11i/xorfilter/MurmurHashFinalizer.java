package me.k11i.xorfilter;

class MurmurHashFinalizer {
    static long hash(long seed, long x) {
        long h = seed + x;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return h ^ (h >>> 33);
    }
}
