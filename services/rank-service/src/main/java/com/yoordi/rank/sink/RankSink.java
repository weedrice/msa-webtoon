package com.yoordi.rank.sink;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

@Component
public class RankSink {

    private static final Logger logger = LoggerFactory.getLogger(RankSink.class);
    
    private final RedissonClient redissonClient;
    private final int ttlFactor;

    public RankSink(RedissonClient redissonClient,
                    @Value("${rank.ttlFactor:3}") int ttlFactor) {
        this.redissonClient = redissonClient;
        this.ttlFactor = ttlFactor;
    }

    public void update(int windowSec, long windowEnd, String contentId, int count) {
        try {
            // ZSET key format: "rank:{windowSec}:{windowEndEpochMillis}"
            String zsetKey = String.format("rank:%d:%d", windowSec, windowEnd);
            
            // Latest pointer key: "rank:latest:{windowSec}"
            String latestKey = String.format("rank:latest:%d", windowSec);
            
            // Add to sorted set with count as score
            var zset = redissonClient.getScoredSortedSet(zsetKey);
            zset.add(count, contentId);
            // Set TTL to auto-expire old windows
            int ttlSeconds = Math.max(windowSec * ttlFactor, windowSec);
            zset.expire(Duration.ofSeconds(ttlSeconds));
            
            // Update latest pointer (with TTL a bit longer than ZSET)
            var latestBucket = redissonClient.getBucket(latestKey);
            latestBucket.set(zsetKey);
            latestBucket.expire(Duration.ofSeconds(ttlSeconds * 2L));
            
            logger.debug("Updated Redis: zsetKey={}, contentId={}, count={}, latestKey={}", 
                        zsetKey, contentId, count, latestKey);
                        
        } catch (Exception e) {
            logger.error("Failed to update Redis rank: windowSec={}, windowEnd={}, contentId={}, count={}", 
                        windowSec, windowEnd, contentId, count, e);
            throw e;
        }
    }
}
