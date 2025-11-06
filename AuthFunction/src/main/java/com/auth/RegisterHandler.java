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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import javax.crypto.SecretKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handler for requests to Lambda function.
 */
public class RegisterHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final Gson gson = new Gson();
    private final DynamoDbTable<User> userTable;

    // Constructor for Lambda runtime (no arguments - AWS calls this)
    public RegisterHandler() {
        this(createDefaultTable());
    }

    // Constructor for testing (accepts mock table)
    public RegisterHandler(DynamoDbTable<User> userTable) {
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

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent event, final Context context) {
        Map<String, String> headers = CommonUtil.getCorsHeaders();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);
        try {
            // KEY CHANGE: Get the POST body
            String body = event.getBody();

            // Basic validation
            // TODO implement strict password
            String email = "";
            String password = "";
            String name = "";
            try {
                Map<String, String> requestData = gson.fromJson(body, Map.class);
                if (requestData == null) throw new JsonSyntaxException("Empty body");

                email = requestData.get("email") != null ? requestData.get("email").trim().toLowerCase() : null;
                password = requestData.get("password");
                name = requestData.get("name") != null ? requestData.get("name").trim() : null;

                // Email regex (simple RFC-like)
                if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$") || email.length() > 254) {
                    throw new IllegalArgumentException("Invalid email format");
                }
                if (name != null && (name.isEmpty() || name.length() > 50)) {
                    throw new IllegalArgumentException("Name must be 1-50 characters");
                }
                if (password == null || password.length() < 8) {
                    throw new IllegalArgumentException("Password must be at least 8 characters");
                }

            } catch (JsonSyntaxException | IllegalArgumentException e) {
                response.setStatusCode(400);
                response.setBody("{\"error\": \"" + e.getMessage() + "\"}");
                return response;
            }

            // Hash password with BCrypt
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));

            // Create user object
            User user = new User();
            user.setUserId(UUID.randomUUID().toString());
            user.setEmail(email.toLowerCase());
            user.setPasswordHash(passwordHash);
            user.setName(name);
            user.setCreatedAt(System.currentTimeMillis());

            Optional<User> existingUserOptional = UserService.findUserByEmail(userTable, email);
            if (existingUserOptional.isPresent()) {
                user = existingUserOptional.get();
                System.out.println("User already exists: " + user.getEmail());
            } else {
                try {
                    userTable.putItem(user);
                    System.out.println("New user created: " + email);
                } catch (ConditionalCheckFailedException e) {
                    // Return existing user or 409 Conflict
                    response.setStatusCode(409);
                    response.setBody("{\"error\": \"User with this email already exists.\"}");
                    return response;
                }
            }

            // Generate JWT token
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", user.getUserId());  // Standard subject claim for user ID
            claims.put("email", user.getEmail());
            if (user.getName() != null) {
                claims.put("name", user.getName());
            }
            long nowMillis = System.currentTimeMillis();
            claims.put("iat", nowMillis / 1000);  // Issued at (seconds)
            claims.put("exp", (nowMillis / 1000) + 3600);  // Expires in 1 hour (adjust as needed)

            String jwtSecret = System.getenv("JWT_SECRET");
            if (jwtSecret == null || jwtSecret.isEmpty()) {
                throw new RuntimeException("JWT_SECRET environment variable is required");
            }
            SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());

            String jwtToken = Jwts.builder()
                    .setClaims(claims)
                    .signWith(signingKey, SignatureAlgorithm.HS256)
                    .compact();

            // Return success response with JWT (don't include password hash!)
            Map<String, Object> responseBody = new HashMap<>();  // Use Object to support token string
            responseBody.put("message", "User registered successfully");
            responseBody.put("email", user.getEmail());
            responseBody.put("token", jwtToken);  // Attach the JWT here

            response.setStatusCode(201);
            response.setBody(gson.toJson(responseBody));  // Gson handles the mixed types
            return response;
        } catch (Exception e) { // For DynamoDB or other errors
            context.getLogger().log("Error creating user:   " + e.getMessage());
            response.setStatusCode(500);
            response.setBody("{\"error\": \"Internal server error\"}");
            return response;
        }
    }
}
