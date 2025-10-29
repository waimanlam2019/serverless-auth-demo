package com.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.auth.model.User;
import com.google.gson.Gson;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.mindrot.jbcrypt.BCrypt;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

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

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        // KEY CHANGE: Get the POST body
        String body = input.getBody();

        // Parse JSON from the body
        Map<String, String> requestData = gson.fromJson(body, Map.class);


        // Extract fields from the POST data
        String email = requestData.get("email");
        String password = requestData.get("password");
        String name = requestData.get("name");

        // Basic validation
        if (email == null || password == null || password.length() < 8) {
            response.setStatusCode(400);
            response.setBody("{\"error\": \"Invalid input. Password must be at least 8 characters.\"}");
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

        // Save to DynamoDB
        userTable.putItem(user);

        // Return success response (don't include password hash!)
        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", "User registered successfully");
        responseBody.put("userId", user.getUserId());
        responseBody.put("email", user.getEmail());

        response.setStatusCode(201);
        response.setBody(gson.toJson(responseBody));
        return response;
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
