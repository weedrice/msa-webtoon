package com.yoordi.rank.service;

import org.redisson.api.RedissonClient;
import org.redisson.api.RScoredSortedSet;
import org.redisson.client.protocol.ScoredEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RankService {

    private static final Logger logger = LoggerFactory.getLogger(RankService.class);
    
    private final RedissonClient redissonClient;
    private final int aggregateReadFactor;

    public RankService(RedissonClient redissonClient,
                       @Value("${rank.aggregateReadFactor:3}") int aggregateReadFactor) {
        this.redissonClient = redissonClient;
        this.aggregateReadFactor = aggregateReadFactor;
    }

    public List<String> getTopContentIds(String window, int n, int aggregate) {
        int windowSec = parseWindowSeconds(window);
        List<String> zsetKeys = getRecentZsetKeys(windowSec, aggregate);
        if (zsetKeys.isEmpty()) return List.of();

        Map<String, Double> sums = aggregateCounts(zsetKeys, n);
        return sums.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }

    public List<Map<String, Object>> getTopContentDetails(String window, int n, int aggregate) {
        int windowSec = parseWindowSeconds(window);
        List<String> zsetKeys = getRecentZsetKeys(windowSec, aggregate);
        if (zsetKeys.isEmpty()) return List.of();

        Map<String, Double> sums = aggregateCounts(zsetKeys, n);
        return sums.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("contentId", e.getKey());
                    m.put("count", e.getValue().intValue());
                    return m;
                })
                .toList();
    }

    private Map<String, Double> aggregateCounts(List<String> zsetKeys, int n) {
        Map<String, Double> sums = new HashMap<>();
        int limitPerSet = Math.max(n * aggregateReadFactor, n);
        for (String key : zsetKeys) {
            RScoredSortedSet<String> set = redissonClient.getScoredSortedSet(key);
            Collection<ScoredEntry<String>> top = set.entryRangeReversed(0, limitPerSet - 1);
            for (ScoredEntry<String> e : top) {
                sums.merge(e.getValue(), e.getScore(), Double::sum);
            }
        }
        return sums;
    }

    private List<String> getRecentZsetKeys(int windowSec, int aggregate) {
        String latestKey = String.format("rank:latest:%s", windowSec);
        String latestZsetKey = (String) redissonClient.getBucket(latestKey).get();
        if (latestZsetKey == null) {
            logger.debug("No ranking data found for windowSec: {}", windowSec);
            return List.of();
        }
        try {
            String[] parts = latestZsetKey.split(":"); // rank:{window}:{end}
            long endMs = Long.parseLong(parts[2]);
            long stepMs = windowSec * 1000L;
            java.util.ArrayList<String> keys = new java.util.ArrayList<>(aggregate);
            for (int i = 0; i < aggregate; i++) {
                long ts = endMs - (i * stepMs);
                keys.add(String.format("rank:%d:%d", windowSec, ts));
            }
            return keys;
        } catch (Exception e) {
            logger.warn("Failed to parse latest zset key: {}", latestZsetKey, e);
            return List.of(latestZsetKey);
        }
    }

    public java.util.Optional<java.util.Map<String, Long>> getAggregationRange(String window, int aggregate) {
        int windowSec = parseWindowSeconds(window);
        String latestKey = String.format("rank:latest:%s", windowSec);
        String latestZsetKey = (String) redissonClient.getBucket(latestKey).get();
        if (latestZsetKey == null) {
            return java.util.Optional.empty();
        }
        try {
            String[] parts = latestZsetKey.split(":");
            long endMs = Long.parseLong(parts[2]);
            long startMs = endMs - (Math.max(1, aggregate) - 1L) * windowSec * 1000L;
            java.util.Map<String, Long> m = new java.util.HashMap<>();
            m.put("start", startMs);
            m.put("end", endMs);
            m.put("windowSec", (long) windowSec);
            return java.util.Optional.of(m);
        } catch (Exception e) {
            logger.warn("Failed to compute aggregation range from key: {}", latestZsetKey, e);
            return java.util.Optional.empty();
        }
    }

    private int parseWindowSeconds(String window) {
        if (window.endsWith("s")) {
            return Integer.parseInt(window.substring(0, window.length() - 1));
        }
        throw new IllegalArgumentException("Invalid window format: " + window);
    }
}
