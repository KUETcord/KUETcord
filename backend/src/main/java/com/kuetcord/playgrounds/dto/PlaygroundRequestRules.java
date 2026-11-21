package com.kuetcord.playgrounds.dto;

/** Shared validation limits for playground requests; they mirror the V3/V9 column sizes. */
final class PlaygroundRequestRules {

    /** http(s) link, same rule as opportunity source URLs. */
    static final String HTTP_URL = "^https?://[^\\s/$.?#][^\\s]*$";

    /** Same as {@link #HTTP_URL} but an empty string is allowed so PATCH can clear a link. */
    static final String HTTP_URL_OR_EMPTY = "^$|" + HTTP_URL;

    /** Tags are stored comma-separated, so a single tag must not contain a comma. */
    static final String TAG = "^[^,]*[^,\\s][^,]*$";

    /** 8 tags x 30 chars + 7 separators fits the 255-char {@code tags} column. */
    static final int MAX_TAGS = 8;
    static final int MAX_TAG_LENGTH = 30;

    private PlaygroundRequestRules() {
    }
}
