package com.kuetcord.notifications.outbox;

/** A notification an {@link OutboxEventHandler} wants to create; the writer inserts drafts in one statement. */
public record NotificationDraft(Long recipientId, String type, String title, String preview, String targetType, Long targetId) {

    public static final int MAX_TITLE = 200;
    public static final int MAX_PREVIEW = 300;

    public NotificationDraft {
        title = truncate(title, MAX_TITLE);
        preview = truncate(preview, MAX_PREVIEW);
    }

    /** Cuts at {@code max} chars without splitting a surrogate pair (emoji stay intact). */
    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        int end = max - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + "…";
    }
}
