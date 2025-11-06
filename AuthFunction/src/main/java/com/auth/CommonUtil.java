package com.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.util.HashMap;
import java.util.Map;

public class CommonUtil {
    private static final String SECRET = System.getenv("JWT_SECRET");

    public static Claims verifyToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            return Jwts.parser()
                    .setSigningKey(SECRET.getBytes())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            throw new SecurityException("Invalid token: " + e.getMessage());
        }
    }

    protected static Map<String, String> getCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");
        return headers;
    }

    protected static APIGatewayProxyResponseEvent getAuthenFailedResponse(){
        Map<String, String> responseHeaders = CommonUtil.getCorsHeaders();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(responseHeaders);
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Unauthorized: Missing or invalid Authorization header");
        response.setStatusCode(403);
        response.setBody(new com.google.gson.Gson().toJson(responseBody));
        return response;
    }

}
