package com.orasa.backend.config;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.UUID;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import com.orasa.backend.common.CacheName;

@Component("businessKeyGenerator")
public class BusinessKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder key = new StringBuilder();
        UUID businessId = null;

        // 1. Try to find businessId via annotation
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof CacheBusinessId && params[i] instanceof UUID uuid) {
                    businessId = uuid;
                    break;
                }
            }
            if (businessId != null) break;
        }

        // 2. Fallback to first UUID parameter if no annotation found
        if (businessId == null) {
            for (Object param : params) {
                if (param instanceof UUID uuid) {
                    businessId = uuid;
                    break;
                }
            }
        }

        if (businessId != null) {
            key.append(businessId);
        }

        // 3. Add other identifying parameters first
        for (Object param : params) {
            if (param != null && !param.equals(businessId)) {
                key.append(CacheName.SEPARATOR).append(formatParam(param));
            }
        }

        // 4. Add Suffix based on return type (unwrapping wrappers like Optional or ResponseEntity) at the end
        if (isCollectionLike(unwrapType(method))) {
            key.append(CacheName.SUFFIX_LIST);
        } else {
            key.append(CacheName.SUFFIX_DETAILS);
        }

        return key.toString();
    }

    private String formatParam(Object param) {
        if (param instanceof org.springframework.data.domain.Pageable p) {
            return "p" + p.getPageNumber() + "_s" + p.getPageSize();
        }
        return param.toString();
    }

    private Type unwrapType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType pt) {
            Class<?> rawType = (Class<?>) pt.getRawType();
            String name = rawType.getName();
            if (name.equals("java.util.Optional") || name.endsWith("ResponseEntity")) {
                Type[] typeArguments = pt.getActualTypeArguments();
                if (typeArguments.length > 0) {
                    return typeArguments[0];
                }
            }
        }
        return returnType;
    }

    private boolean isCollectionLike(Type type) {
        Class<?> clazz = null;
        if (type instanceof Class<?>) {
            clazz = (Class<?>) type;
        } else if (type instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?>) {
                clazz = (Class<?>) pt.getRawType();
            }
        }

        if (clazz == null) return false;

        return Collection.class.isAssignableFrom(clazz) || 
               Iterable.class.isAssignableFrom(clazz) ||
               clazz.getName().contains("Page") || 
               clazz.getName().contains("Slice");
    }
}
