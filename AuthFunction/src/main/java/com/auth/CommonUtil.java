package com.auth;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    protected static Map<String, String> getCorsHeaders(String origin) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        System.out.println("Origin: " + origin);
        // Define allowed origins
        List<String> allowedOrigins = Arrays.asList(
                "http://localhost:4000"      // local dev
                //"https://yourdomain.com",     //TODO production domain
        );

        // Dynamically set Access-Control-Allow-Origin if the request matches
        if (allowedOrigins.contains(origin)) {
            headers.put("Access-Control-Allow-Origin", origin);
        }

        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        headers.put("Access-Control-Allow-Credentials", "true"); // optional, if you send cookies or JWT

        return headers;
    }


    protected static APIGatewayProxyResponseEvent getAuthenFailedResponse(String origin){
        Map<String, String> responseHeaders = CommonUtil.getCorsHeaders(origin);
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(responseHeaders);
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Unauthorized: Missing or invalid Authorization header");
        response.setStatusCode(403);
        response.setBody(new com.google.gson.Gson().toJson(responseBody));
        return response;
    }

    protected static APIGatewayProxyResponseEvent getAuthenFailedResponse(APIGatewayProxyRequestEvent event){
        String origin = null;
        if (event.getHeaders() != null) {
            origin = event.getHeaders().get("origin");
        }
        return getAuthenFailedResponse(origin);
    }

    protected static Map<String, String> getCorsHeaders(APIGatewayProxyRequestEvent event){
        String origin = null;
        if (event.getHeaders() != null) {
            origin = event.getHeaders().get("origin");
        }

        String stage = System.getenv("STAGE");
        if (origin == null && ( stage!=null && stage.equals("dev"))) {
            origin = "http://localhost:4000";
        }
        return getCorsHeaders(origin);
    }

}
