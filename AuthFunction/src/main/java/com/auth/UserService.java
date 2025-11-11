package com.auth;

import com.auth.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.internal.conditional.EqualToConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserService {
    protected static Optional<User> findUserByUserId(DynamoDbTable<User> userTable, String userId) {
        return Optional.ofNullable(userTable.getItem(Key.builder().partitionValue(userId).build()));
    }

    protected static Optional<User> findUserByEmail(DynamoDbTable<User> userTable, String email) {
        DynamoDbIndex<User> emailGSI = userTable.index("EmailIndex");

        SdkIterable<Page<User>> results = emailGSI.query(new EqualToConditional(Key.builder()
                .partitionValue(email.toLowerCase())
                .build()));

        User existingUser = null;
        for (Page<User> page : results) {
            if (!page.items().isEmpty()) {
                existingUser = page.items().getFirst();
                break;
            }
        }
        return Optional.ofNullable(existingUser);
    }

    protected static String generateJwtToken(User user) {
        // Generate JWT token
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", user.getUserId());
        claims.put("email", user.getEmail());
        if (user.getName() != null) {
            claims.put("name", user.getName());
        }
        long nowMillis = System.currentTimeMillis();
        claims.put("iat", nowMillis / 1000);
        claims.put("exp", (nowMillis / 1000) + 3600);  // 1 hour expiry

        String jwtSecret = System.getenv("JWT_SECRET");
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            throw new RuntimeException("JWT_SECRET environment variable is required");
        }
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        return Jwts.builder()
                .setClaims(claims)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
