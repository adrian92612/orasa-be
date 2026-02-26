package com.orasa.backend.service;

import java.util.Set;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;

    public void evict(String cacheName, Object key) {
        redisTemplate.delete(cacheName + "::" + key);
    }

    public void evictAll(String cacheName) {
        Set<String> keys = redisTemplate.keys(cacheName + "::*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
