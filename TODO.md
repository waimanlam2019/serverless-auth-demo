# TODO List

## Completed ✅

### Registration Feature
- ✅ AWS SAM project setup
- ✅ Maven dependencies configured
- ✅ User model with DynamoDB annotations
- ✅ DynamoDB table in CloudFormation
- ✅ RegisterHandler implementation
- ✅ Password hashing with BCrypt
- ✅ Input validation
- ✅ Unit tests with mocks
- ✅ Local testing working

## Next: Login Feature 🚧

- [ ] Add JWT library to pom.xml
- [ ] Create LoginHandler class
- [ ] Query user by email (EmailIndex)
- [ ] Verify password with BCrypt
- [ ] Generate JWT access token
- [ ] Generate JWT refresh token
- [ ] Add login endpoint to template.yaml
- [ ] Write LoginHandler tests
- [ ] Test login flow

## Future Features

### Token Refresh
- [ ] Create RefreshTokenHandler
- [ ] Validate refresh tokens
- [ ] Issue new access tokens
- [ ] Add /refresh endpoint

### Protected Endpoints
- [ ] GetUserHandler (GET /user)
- [ ] UpdateUserHandler (PUT /user)
- [ ] JWT validation utility
- [ ] Authorization middleware

### Validations
- [ ] Email format validation
- [ ] Duplicate email checking
- [ ] Password strength requirements
- [ ] Return 409 for duplicates

### Security
- [ ] Track failed login attempts
- [ ] Account lockout mechanism
- [ ] Rate limiting
- [ ] Request logging
- [ ] CloudWatch monitoring

### Deployment
- [ ] Deploy to AWS
- [ ] CloudWatch logs setup
- [ ] Error alarms
- [ ] X-Ray tracing
- [ ] Production testing

## Technical Improvements

- [ ] Custom exception classes
- [ ] Validation service layer
- [ ] Better error messages
- [ ] API documentation
- [ ] Integration tests

## Questions

- JWT secret storage method?
- Token expiry times?
- Password complexity rules?
- Rate limit thresholds?
