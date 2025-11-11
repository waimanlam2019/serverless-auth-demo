package com.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth.model.User;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.mindrot.jbcrypt.BCrypt;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

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

        Map<String, String> responseHeaders = CommonUtil.getCorsHeaders(event);
        responseHeaders.put("Content-Type", "application/json");
        responseHeaders.put("X-Custom-Header", "application/json");
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(responseHeaders);
        try {
            // KEY CHANGE: Get the POST body
            String body = event.getBody();

            // Basic validation
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
                if (password == null ||
                        !password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).{8,}$")) {
                    throw new IllegalArgumentException(
                            "Password must be at least 8 characters long and include uppercase, lowercase, and a symbol"
                    );
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
                System.out.println("User already exists: " + user.getEmail());
                response.setStatusCode(400);
                response.setBody("{\"error\": \"User already exists\"}");
                return response;
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

            // Return success response with JWT (don't include password hash!)
            Map<String, Object> responseBody = new HashMap<>();  // Use Object to support token string
            responseBody.put("message", "User registered successfully");
            responseBody.put("email", user.getEmail());
            responseBody.put("token", UserService.generateJwtToken(user));  // Attach the JWT here

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
