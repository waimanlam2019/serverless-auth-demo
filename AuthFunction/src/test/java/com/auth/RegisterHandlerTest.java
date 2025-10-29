package com.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth.model.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RegisterHandlerTest {

    @Mock
    private DynamoDbTable<User> mockUserTable;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger;

    private RegisterHandler handler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getLogger()).thenReturn(mockLogger);
        handler = new RegisterHandler(mockUserTable);
    }

    @Test
    public void testSuccessfulRegistration() {
        // Arrange
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String requestBody = "{\"email\":\"test@example.com\",\"password\":\"SecurePass123\",\"name\":\"Test User\"}";
        request.setBody(requestBody);

        // Act
        APIGatewayProxyResponseEvent result = handler.handleRequest(request, mockContext);

        // Assert
        assertEquals(201, result.getStatusCode().intValue());
        assertEquals("application/json", result.getHeaders().get("Content-Type"));

        String content = result.getBody();
        assertNotNull(content);
        assertTrue(content.contains("\"message\""));
        assertTrue(content.contains("User registered successfully"));
        assertTrue(content.contains("\"userId\""));
        assertTrue(content.contains("\"email\""));

        // Verify DynamoDB was called - use ArgumentCaptor to avoid ambiguity
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(mockUserTable, times(1)).putItem(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("Test User", savedUser.getName());
        assertNotNull(savedUser.getUserId());
        assertNotNull(savedUser.getPasswordHash());
        assertTrue(savedUser.getPasswordHash().startsWith("$2a$"));
    }

    @Test
    public void testInvalidPasswordTooShort() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String requestBody = "{\"email\":\"test@example.com\",\"password\":\"short\",\"name\":\"Test User\"}";
        request.setBody(requestBody);

        APIGatewayProxyResponseEvent result = handler.handleRequest(request, mockContext);

        assertEquals(400, result.getStatusCode().intValue());
        String content = result.getBody();
        assertTrue(content.contains("\"error\""));
        assertTrue(content.contains("Invalid input"));

        // Verify DynamoDB was NOT called - use times(0) to avoid ambiguity
        verify(mockUserTable, times(0)).putItem((User) any());
    }

    @Test
    public void testMissingEmail() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String requestBody = "{\"password\":\"SecurePass123\",\"name\":\"Test User\"}";
        request.setBody(requestBody);

        APIGatewayProxyResponseEvent result = handler.handleRequest(request, mockContext);

        assertEquals(400, result.getStatusCode().intValue());
        verify(mockUserTable, times(0)).putItem((User) any());
    }

    @Test
    public void testEmailConvertedToLowercase() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        String requestBody = "{\"email\":\"TEST@EXAMPLE.COM\",\"password\":\"SecurePass123\",\"name\":\"Test User\"}";
        request.setBody(requestBody);

        handler.handleRequest(request, mockContext);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(mockUserTable).putItem(userCaptor.capture());

        assertEquals("test@example.com", userCaptor.getValue().getEmail());
    }
}
