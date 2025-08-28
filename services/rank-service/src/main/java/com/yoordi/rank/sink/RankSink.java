package com.yoordi.rank.sink;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RankSink {

    private static final Logger logger = LoggerFactory.getLogger(RankSink.class);
    
    private final RedissonClient redissonClient;

    public RankSink(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public void update(int windowSec, long windowEnd, String contentId, int count) {
        try {
            // ZSET key format: "rank:{windowSec}:{windowEndEpochMillis}"
            String zsetKey = String.format("rank:%d:%d", windowSec, windowEnd);
            
            // Latest pointer key: "rank:latest:{windowSec}"
            String latestKey = String.format("rank:latest:%d", windowSec);
            
            // Add to sorted set with count as score
            redissonClient.getScoredSortedSet(zsetKey).add(count, contentId);
            
            // Update latest pointer
            redissonClient.getBucket(latestKey).set(zsetKey);
            
            logger.debug("Updated Redis: zsetKey={}, contentId={}, count={}, latestKey={}", 
                        zsetKey, contentId, count, latestKey);
                        
        } catch (Exception e) {
            logger.error("Failed to update Redis rank: windowSec={}, windowEnd={}, contentId={}, count={}", 
                        windowSec, windowEnd, contentId, count, e);
            throw e;
        }
    }
}