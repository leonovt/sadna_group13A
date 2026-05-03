package com.sadna.group13a.infrastructure;

import com.sadna.group13a.application.Interfaces.IAuth;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class AuthImpl implements IAuth {

    // The new 0.12.x way to generate a secure key for HS256
    private final SecretKey key = Jwts.SIG.HS256.key().build();
    
    // Token valid for 24 hours
    private final long expirationTime = 1000 * 60 * 60 * 24; 

    @Override
    public String generateToken(String userId) {
        return Jwts.builder()
                .subject(userId)                               // Replaces setSubject()
                .issuedAt(new Date(System.currentTimeMillis())) // Replaces setIssuedAt()
                .expiration(new Date(System.currentTimeMillis() + expirationTime)) // Replaces setExpiration()
                .signWith(key)
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(key)        // Replaces setSigningKey()
                .build()                // Builder pattern moved to the parser
                .parseSignedClaims(token); // Replaces parseClaimsJws()
            return true;
        } catch (Exception e) {
            // Token is expired, malformed, or has a bad signature
            return false;
        }
    }

    @Override
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Helper method to extract specific claims
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload(); // Replaces getBody()
                
        return claimsResolver.apply(claims);
    }
}