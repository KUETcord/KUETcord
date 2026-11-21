package com.kuetcord.cthub.state;

import com.kuetcord.cthub.model.CtStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the concrete {@link CtState} handler for a {@link CtStatus} in O(1) via {@link EnumMap}.
 */
@Component
public class CtStateResolver {

    private final Map<CtStatus, CtState> states = new EnumMap<>(CtStatus.class);

    public CtStateResolver(List<CtState> stateBeans) {
        for (CtState state : stateBeans) {
            states.put(state.getStatus(), state);
        }
    }

    public CtState resolve(CtStatus status) {
        CtState state = states.get(status);
        if (state == null) {
            throw new IllegalStateException("No CtState handler registered for status: " + status);
        }
        return state;
    }
}
