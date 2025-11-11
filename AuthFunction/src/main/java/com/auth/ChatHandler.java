package com.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import io.jsonwebtoken.Claims;

import java.util.HashMap;
import java.util.Map;

public class ChatHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final Gson gson = new Gson();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        Map<String, String> requestHeaders = (Map<String, String>) event.getHeaders();
        String authHeader = requestHeaders.get("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return CommonUtil.getAuthenFailedResponse(event);
        }

        try {
            Claims claims = CommonUtil.verifyToken(authHeader);

            Map<String, String> responseHeaders = CommonUtil.getCorsHeaders(event);
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                    .withHeaders(responseHeaders);
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "This is a dummy message from ChatHandler");//TODO implement real logic
            response.setStatusCode(200);
            response.setBody(gson.toJson(responseBody));
            return response;
        }catch (SecurityException e){
            return CommonUtil.getAuthenFailedResponse(event);
        }
    }


}
