package com.kuetcord.studycircles.service;

import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.studycircles.access.StudyCircleAccessPolicy;
import com.kuetcord.studycircles.dto.CreateRevisionItemRequest;
import com.kuetcord.studycircles.dto.RevisionItemDto;
import com.kuetcord.studycircles.model.RevisionItem;
import com.kuetcord.studycircles.repository.RevisionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** The circle's shared revision checklist. Members only, like sessions and resources. */
@Service
@RequiredArgsConstructor
@Transactional
public class RevisionItemService {

    private final RevisionItemRepository itemRepository;
    private final StudyCircleAccessPolicy accessPolicy;

    @Transactional(readOnly = true)
    public List<RevisionItemDto> list(Long circleId, Long userId) {
        accessPolicy.requireMember(circleId, userId);
        return itemRepository.findItemDtos(circleId);
    }

    public RevisionItemDto add(Long circleId, Long userId, CreateRevisionItemRequest request) {
        accessPolicy.requireMember(circleId, userId);
        RevisionItem saved = itemRepository.saveAndFlush(RevisionItem.builder()
                .studyCircleId(circleId)
                .title(request.title().strip())
                .createdBy(userId)
                .build());
        return itemRepository.findItemDto(saved.getId()).orElseThrow();
    }

    public RevisionItemDto setDone(Long circleId, Long itemId, Long userId, boolean done) {
        accessPolicy.requireMember(circleId, userId);
        // Scoped by circle: an item id from another circle is "not found", never editable through this one.
        RevisionItem item = itemRepository.findByIdAndStudyCircleId(itemId, circleId)
                .orElseThrow(() -> new ResourceNotFoundException("Revision item not found"));
        if (item.markDone(done, userId, Instant.now())) {
            itemRepository.saveAndFlush(item);
        }
        return itemRepository.findItemDto(item.getId()).orElseThrow();
    }
}
