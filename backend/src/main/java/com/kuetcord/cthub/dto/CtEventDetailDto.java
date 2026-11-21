package com.kuetcord.cthub.dto;

import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtEventHistory;

import java.util.List;

public record CtEventDetailDto(
        CtEventSummaryDto event,
        List<CtEventHistoryDto> history
) {
    public static CtEventDetailDto from(CtEvent event, List<CtEventHistory> historyRows) {
        return new CtEventDetailDto(
                CtEventSummaryDto.from(event),
                historyRows.stream().map(CtEventHistoryDto::from).toList()
        );
    }
}
