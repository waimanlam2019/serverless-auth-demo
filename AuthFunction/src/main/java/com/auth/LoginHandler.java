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
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(CommonUtil.getCorsHeaders(event));

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
            Optional<User> existingUserOptional = UserService.findUserByEmail(userTable, email);
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

            // Success response (don't include password hash)
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Login successful");
            responseBody.put("email", user.getEmail());
            if (user.getName() != null) {
                responseBody.put("name", user.getName());
            }
            responseBody.put("token", UserService.generateJwtToken(user));

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

}
