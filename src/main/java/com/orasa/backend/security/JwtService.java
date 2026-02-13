package com.orasa.backend.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
  
  @Value("${jwt.secret}")
  private String secretKey;

  @Value("${jwt.expiration}")
  private long jwtExpiration;
  
  @org.springframework.beans.factory.annotation.Autowired
  private Clock clock;

  public String generateToken(UUID userId, String username, String role) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("username", username);
    claims.put("role", role);
    
    return Jwts.builder()
        .subject(userId.toString())
        .claims(claims)
        .issuedAt(Date.from(Instant.now(clock)))
        .expiration(Date.from(Instant.now(clock).plusMillis(jwtExpiration)))
        .signWith(getSigningKey())
        .compact();
  }

  public String extractUserId(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public String extractRole(String token) {
    return extractClaim(token, claims -> claims.get("role", String.class));
  }

  public boolean isTokenValid(String token) {
    try {
      return !isTokenExpired(token);
    } catch (Exception e) {
      return false;
    }
  }

  private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser()
      .verifyWith(getSigningKey())
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }

  private boolean isTokenExpired(String token) {
    return extractExpiration(token).before(Date.from(Instant.now(clock)));
  }

  private Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }
}
