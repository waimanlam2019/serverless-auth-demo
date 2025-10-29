# Serverless Authentication Demo

A serverless authentication system built with AWS Lambda, API Gateway, and DynamoDB.

## Current Status

✅ User registration endpoint complete and working

## Architecture

- AWS Lambda (Java 21)
- API Gateway REST API
- DynamoDB for user storage
- BCrypt for password hashing
- AWS SAM for infrastructure

## Project Structure

serverless-auth-demo/
├── template.yaml
├── AuthFunction/
│ ├── pom.xml
│ └── src/
│ ├── main/java/com/auth/
│ │ ├── RegisterHandler.java
│ │ └── model/User.java
│ └── test/java/com/auth/
│ └── RegisterHandlerTest.java

## Setup

Install prerequisites:
- Java 21
- Maven
- AWS SAM CLI
- AWS CLI (configured)

Build and deploy:
sam build
sam deploy --guided

text

## API Endpoints

### POST /register

Register a new user

Request body:
{
"email": "user@example.com",
"password": "SecurePass123",
"name": "John Doe"
}

text

Success response (201):
{
"message": "User registered successfully",
"userId": "generated-uuid",
"email": "user@example.com"
}

text

Error response (400):
{
"error": "Invalid input. Password must be at least 8 characters."
}

text

## Local Testing

Start local API:
sam local start-api

text

Test with curl:
curl -X POST http://localhost:3000/register
-H "Content-Type: application/json"
-d '{"email":"test@example.com","password":"SecurePass123","name":"Test"}'

text

Or use Postman with the same endpoint and body.

## Running Tests

cd AuthFunction
mvn test

text

## Tech Stack

- Java 21
- AWS SDK v2
- DynamoDB Enhanced Client
- BCrypt (jbcrypt)
- Jackson for JSON
- JUnit 4 and Mockito for testing

## Security

- Passwords hashed with BCrypt (12 rounds)
- Emails normalized to lowercase
- Input validation
- No sensitive data in logs