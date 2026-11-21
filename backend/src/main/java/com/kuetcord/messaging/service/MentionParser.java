package com.kuetcord.messaging.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code @username} tokens from message text.
 * <p>
 * Usernames follow the identity module's alphabet {@code [A-Za-z0-9._-]}. A mention must start
 * the text or follow a non-word character, so emails like {@code rahim@stud.kuet.ac.bd} are not
 * mentions. Trailing dots/dashes are trimmed ({@code "thanks @sadia."} -> {@code sadia}).
 */
public final class MentionParser {

    public static final int MAX_MENTIONS_PER_MESSAGE = 20;

    private static final Pattern MENTION = Pattern.compile("(?<![\\w@.])@([A-Za-z0-9._-]{1,50})");

    private MentionParser() {
    }

    /** Distinct lowercase usernames in order of first appearance, capped at {@value #MAX_MENTIONS_PER_MESSAGE}. */
    public static Set<String> parse(String content) {
        Set<String> usernames = new LinkedHashSet<>();
        if (content == null || content.indexOf('@') < 0) {
            return usernames;
        }
        Matcher matcher = MENTION.matcher(content);
        while (matcher.find() && usernames.size() < MAX_MENTIONS_PER_MESSAGE) {
            String name = matcher.group(1).replaceAll("[.\\-]+$", "");
            if (!name.isEmpty()) {
                usernames.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return usernames;
    }
}
