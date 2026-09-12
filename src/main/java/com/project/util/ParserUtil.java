package com.project.util;

import com.project.dao.entities.Conversation;

import java.util.List;
import java.util.StringJoiner;

public class ParserUtil {
    public static String parseConversationListToString(List<Conversation> conversationList) {
        StringJoiner stringJoiner = new StringJoiner(",");
        for (Conversation conversation : conversationList) {
            stringJoiner.add(
                    "{" +
                            "Role:" + conversation.getRole() +
                            ",Message:" + conversation.getMessage() +
                            ",Time:" + conversation.getCreationTime()
                            + "}"
            );
        }
        return stringJoiner.toString();
    }
}
