package com.orasa.backend.service;

import com.orasa.backend.common.CacheName;
import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;

    public void evict(String cacheName, Object key) {
        redisTemplate.delete(cacheName + CacheName.REGION_SEPARATOR + key);
    }

    public void evictAll(String cacheName) {
        String pattern = cacheName + CacheName.REGION_SEPARATOR + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public void evictAll(String cacheName, Object businessId) {
        String pattern = cacheName + CacheName.REGION_SEPARATOR + businessId + CacheName.SEPARATOR + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
