package com.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth.model.User;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RegisterHandlerTest {

    @Mock
    private DynamoDbTable<User> mockUserTable;

    @Mock
    private DynamoDbIndex<User> mockEmailGSI;

    @Mock
    private Context mockContext;

    @Mock
    private LambdaLogger mockLogger;

    @Test
    public void returns400_whenPasswordTooShort() {
        RegisterHandler handler = new RegisterHandler(mock(DynamoDbTable.class));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withBody("{\"email\":\"bob@example.com\",\"password\":\"123\",\"name\":\"Bob\"}");

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, mock(Context.class));

        assertEquals(Integer.valueOf(400), response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid input"));
    }


}
