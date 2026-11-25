package com.kuetcord.pulse.service;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.dto.UserRefDto;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.pulse.dto.AnnouncementDto;
import com.kuetcord.pulse.dto.AnnouncementPageDto;
import com.kuetcord.pulse.dto.CreateAnnouncementRequest;
import com.kuetcord.pulse.model.Announcement;
import com.kuetcord.pulse.model.AnnouncementCategory;
import com.kuetcord.pulse.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Campus Pulse feed: newest first, filterable by category and organization; author names batch-loaded. */
@Service
@RequiredArgsConstructor
@Transactional
public class AnnouncementService {

    static final int MAX_PAGE_SIZE = 50;

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AnnouncementPageDto feed(AnnouncementCategory category, String organization, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Announcement> result = announcementRepository.findAll(
                AnnouncementRepository.matching(category, organization),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));

        // One query for every author on the page, not one per card.
        Map<Long, String> authorNames = userRepository.findAllById(result.getContent().stream()
                        .map(Announcement::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getDisplayName));

        List<AnnouncementDto> items = result.getContent().stream()
                .map(announcement -> toDto(announcement, authorNames::get))
                .toList();
        return new AnnouncementPageDto(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AnnouncementDto get(Long id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));
        return toDto(announcement, this::displayName);
    }

    public AnnouncementDto publish(Long authorId, CreateAnnouncementRequest request) {
        Announcement saved = announcementRepository.saveAndFlush(Announcement.builder()
                .organization(request.organization().strip())
                .category(request.category())
                .title(request.title().strip())
                .body(request.body().strip())
                .sourceUrl(request.sourceUrl() == null || request.sourceUrl().isBlank() ? null : request.sourceUrl().strip())
                .authorId(authorId)
                .build());
        return toDto(saved, this::displayName);
    }

    private String displayName(Long userId) {
        return userRepository.findById(userId).map(User::getDisplayName).orElse(null);
    }

    private static AnnouncementDto toDto(Announcement announcement, Function<Long, String> nameLookup) {
        return new AnnouncementDto(
                announcement.getId(),
                announcement.getOrganization(),
                announcement.getCategory(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getSourceUrl(),
                announcement.getPublishedAt(),
                UserRefDto.of(announcement.getAuthorId(), nameLookup.apply(announcement.getAuthorId())));
    }
}
