package com.yoordi.rank.service;

import org.redisson.api.RedissonClient;
import org.redisson.api.RScoredSortedSet;
import org.redisson.client.protocol.ScoredEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RankService {

    private static final Logger logger = LoggerFactory.getLogger(RankService.class);
    
    private final RedissonClient redissonClient;

    public RankService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public List<String> getTopContentIds(String window, int n) {
        String latestKey = String.format("rank:latest:%s", parseWindowSeconds(window));
        String zsetKey = (String) redissonClient.getBucket(latestKey).get();
        
        if (zsetKey == null) {
            logger.debug("No ranking data found for window: {}", window);
            return List.of();
        }
        
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(zsetKey);
        Collection<String> topEntries = sortedSet.valueRangeReversed(0, n - 1);
        
        return List.copyOf(topEntries);
    }

    public List<Map<String, Object>> getTopContentDetails(String window, int n) {
        String latestKey = String.format("rank:latest:%s", parseWindowSeconds(window));
        String zsetKey = (String) redissonClient.getBucket(latestKey).get();
        
        if (zsetKey == null) {
            logger.debug("No ranking data found for window: {}", window);
            return List.of();
        }
        
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(zsetKey);
        Collection<ScoredEntry<String>> topEntries = sortedSet.entryRangeReversed(0, n - 1);
        
        return topEntries.stream()
                        .map(entry -> {
                            Map<String, Object> detail = new HashMap<>();
                            detail.put("contentId", entry.getValue());
                            detail.put("count", entry.getScore().intValue());
                            return detail;
                        })
                        .toList();
    }

    private int parseWindowSeconds(String window) {
        if (window.endsWith("s")) {
            return Integer.parseInt(window.substring(0, window.length() - 1));
        }
        throw new IllegalArgumentException("Invalid window format: " + window);
    }
}