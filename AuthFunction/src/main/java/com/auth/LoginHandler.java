package com.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth.model.User;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.mindrot.jbcrypt.BCrypt;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.internal.conditional.EqualToConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handler for login requests to Lambda function.
 */
public class LoginHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final Gson gson = new Gson();
    private final DynamoDbTable<User> userTable;

    // Constructor for Lambda runtime (no arguments - AWS calls this)
    public LoginHandler() {
        this(createDefaultTable());
    }

    // Constructor for testing (accepts mock table)
    public LoginHandler(DynamoDbTable<User> userTable) {
        this.userTable = userTable;
    }

    // Helper method to create real DynamoDB table
    private static DynamoDbTable<User> createDefaultTable() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        String tableName = System.getenv("USERS_TABLE");
        System.out.println("Table Name: "  + tableName);
        return enhancedClient.table(tableName, TableSchema.fromBean(User.class));
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Map<String, String> headers = getCorsHeaders();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        try {
            String body = event.getBody();
            if (body == null || body.isEmpty()) {
                response.setStatusCode(400);
                response.setBody("{\"error\": \"Request body is required\"}");
                return response;
            }

            Map<String, String> requestData;
            try {
                requestData = gson.fromJson(body, Map.class);
                if (requestData == null) throw new JsonSyntaxException("Empty body");
            } catch (JsonSyntaxException e) {
                response.setStatusCode(400);
                response.setBody("{\"error\": \"Invalid JSON in request body\"}");
                return response;
            }

            String email = requestData.get("email");
            String password = requestData.get("password");

            if (email == null || email.trim().isEmpty()) {
                response.setStatusCode(400);
                response.setBody("{\"error\": \"Email is required\"}");
                return response;
            }
            if (password == null || password.isEmpty()) {
                response.setStatusCode(400);
                response.setBody("{\"error\": \"Password is required\"}");
                return response;
            }

            // Normalize email
            email = email.trim().toLowerCase();

            // Basic email validation (same as registration)
            if (!email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$") || email.length() > 254) {
                response.setStatusCode(400);
                response.setBody("{\"error\": \"Invalid email format\"}");
                return response;
            }

            // Find user by email using GSI
            Optional<User> existingUserOptional = findUserByEmail(email);
            if (existingUserOptional.isEmpty()) {
                response.setStatusCode(401);
                response.setBody("{\"error\": \"Invalid email or password\"}");
                return response;
            }

            User user = existingUserOptional.get();

            // Verify password
            if (!BCrypt.checkpw(password, user.getPasswordHash())) {
                response.setStatusCode(401);
                response.setBody("{\"error\": \"Invalid email or password\"}");
                return response;
            }

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

            String jwtToken = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey, SignatureAlgorithm.HS256)
                    .compact();

            // Success response (don't include password hash)
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Login successful");
            responseBody.put("email", user.getEmail());
            if (user.getName() != null) {
                responseBody.put("name", user.getName());
            }
            responseBody.put("token", jwtToken);

            response.setStatusCode(200);
            response.setBody(gson.toJson(responseBody));
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            context.getLogger().log("Error during login: " + e.getMessage());
            response.setStatusCode(500);
            response.setBody("{\"error\": \"Internal server error\"}");
            return response;
        }
    }

    private Optional<User> findUserByUserId(String userId) {
        return Optional.ofNullable(userTable.getItem(Key.builder().partitionValue(userId).build()));
    }

    private Optional<User> findUserByEmail(String email) {
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

    private Map<String, String> getCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");
        return headers;
    }
}
