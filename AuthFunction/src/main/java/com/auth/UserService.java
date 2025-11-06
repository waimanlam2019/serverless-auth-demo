package com.auth;

import com.auth.model.User;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.internal.conditional.EqualToConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.util.Optional;

public class UserService {
    protected static Optional<User> findUserByUserId(DynamoDbTable<User> userTable, String userId) {
        return Optional.ofNullable(userTable.getItem(Key.builder().partitionValue(userId).build()));
    }

    protected static Optional<User> findUserByEmail(DynamoDbTable<User> userTable, String email) {
        DynamoDbIndex<User> emailGSI = userTable.index("EmailIndex");

        SdkIterable<Page<User>> results = emailGSI.query(new EqualToConditional(Key.builder()
                .partitionValue(email.toLowerCase())
                .build()));

        User existingUser = null;
        for (Page<User> page : results) {
            if (!page.items().isEmpty()) {
                existingUser = page.items().getFirst();
                break;
            }
        }
        return Optional.ofNullable(existingUser);
    }
}
