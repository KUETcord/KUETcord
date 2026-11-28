package com.kuetcord.termhub.service;

import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.academic.YearTerm;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.cthub.dto.RoomAllocationDto;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import com.kuetcord.termhub.dto.TermFinalScheduleDto;
import com.kuetcord.termhub.dto.UpsertTermFinalScheduleRequest;
import com.kuetcord.termhub.model.TermFinalEntry;
import com.kuetcord.termhub.model.TermFinalSchedule;
import com.kuetcord.termhub.repository.TermFinalScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TermFinalService {

    private final TermFinalScheduleRepository scheduleRepo;
    private final CourseOfferingRepository courseRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public List<TermFinalScheduleDto> list(String dept, String yearTerm, String batch, String session) {
        String normalizedTerm = normalizeBlank(yearTerm) != null ? YearTerm.normalizeNullable(yearTerm) : null;
        return scheduleRepo.search(normalizeBlank(dept), normalizedTerm, normalizeBlank(batch), normalizeBlank(session))
                .stream()
                .map(TermFinalScheduleDto::fromEntity)
                .toList();
    }

    @Transactional
    public TermFinalScheduleDto create(Long actorId, UpsertTermFinalScheduleRequest req) {
        User actor = findActor(actorId);
        String dept = req.targetDepartment().trim().toUpperCase(Locale.ROOT);
        checkDeptAuthority(actor, dept);
        checkClockRange(req);

        List<RoomAllocationDto> sanitizedAllocations = sanitizeRoomAllocations(req.roomAllocations());
        String summaryRoom = RoomAllocationDto.summarize(sanitizedAllocations);
        String effectiveRoom = summaryRoom != null ? summaryRoom : normalizeBlank(req.examRoom());

        TermFinalSchedule schedule = TermFinalSchedule.builder()
                .title(req.title().trim())
                .targetDepartment(dept)
                .yearTerm(YearTerm.normalizeNullable(req.yearTerm()))
                .batch(req.batch().trim())
                .session(req.session().trim())
                .examStartTime(req.examStartTime())
                .examEndTime(req.examEndTime())
                .examRoom(effectiveRoom)
                .roomAllocations(sanitizedAllocations)
                .createdBy(actor)
                .build();
        schedule.replaceEntries(buildExamRows(req.entries()));
        return TermFinalScheduleDto.fromEntity(scheduleRepo.save(schedule));
    }

    @Transactional
    public TermFinalScheduleDto update(Long actorId, Long scheduleId, UpsertTermFinalScheduleRequest req) {
        User actor = findActor(actorId);
        TermFinalSchedule schedule = scheduleRepo.findWithEntriesById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Term schedule not found: " + scheduleId));

        String dept = req.targetDepartment().trim().toUpperCase(Locale.ROOT);
        checkDeptAuthority(actor, schedule.getTargetDepartment());
        checkDeptAuthority(actor, dept);
        checkClockRange(req);

        List<RoomAllocationDto> sanitizedAllocations = sanitizeRoomAllocations(req.roomAllocations());
        String summaryRoom = RoomAllocationDto.summarize(sanitizedAllocations);
        String effectiveRoom = summaryRoom != null ? summaryRoom : normalizeBlank(req.examRoom());

        schedule.setTitle(req.title().trim());
        schedule.setTargetDepartment(dept);
        schedule.setYearTerm(YearTerm.normalizeNullable(req.yearTerm()));
        schedule.setBatch(req.batch().trim());
        schedule.setSession(req.session().trim());
        schedule.setExamStartTime(req.examStartTime());
        schedule.setExamEndTime(req.examEndTime());
        schedule.setExamRoom(effectiveRoom);
        schedule.setRoomAllocations(sanitizedAllocations);
        schedule.replaceEntries(buildExamRows(req.entries()));
        return TermFinalScheduleDto.fromEntity(scheduleRepo.save(schedule));
    }

    @Transactional
    public void delete(Long actorId, Long scheduleId) {
        User actor = findActor(actorId);
        TermFinalSchedule schedule = scheduleRepo.findWithEntriesById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Term schedule not found: " + scheduleId));
        checkDeptAuthority(actor, schedule.getTargetDepartment());
        scheduleRepo.delete(schedule);
    }

    private List<TermFinalEntry> buildExamRows(List<UpsertTermFinalScheduleRequest.ExamRowRequest> rows) {
        List<TermFinalEntry> mapped = new ArrayList<>(rows.size());
        for (UpsertTermFinalScheduleRequest.ExamRowRequest row : rows) {
            CourseOffering offering = row.courseOfferingId() == null
                    ? null
                    : courseRepo.findById(row.courseOfferingId())
                            .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + row.courseOfferingId()));
            String code = normalizeBlank(row.courseCode());
            String title = normalizeBlank(row.courseTitle());
            if (offering != null) {
                if (code == null) {
                    code = offering.getCourseCode();
                }
                if (title == null) {
                    title = offering.getCourseTitle();
                }
            }
            if (code == null || title == null) {
                throw new BadRequestException("Provide a course offering or both course code and title");
            }
            mapped.add(TermFinalEntry.builder()
                    .examDate(row.examDate())
                    .courseOffering(offering)
                    .courseCode(code)
                    .courseTitle(title)
                    .examRoom(normalizeBlank(row.examRoom()))
                    .build());
        }
        return mapped;
    }

    private List<RoomAllocationDto> sanitizeRoomAllocations(List<RoomAllocationDto> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<RoomAllocationDto> sanitized = new ArrayList<>();
        for (RoomAllocationDto entry : raw) {
            if (entry == null || entry.room() == null || entry.room().isBlank()) {
                continue;
            }
            Integer start = entry.rollStart();
            Integer end = entry.rollEnd();
            if (start != null && end != null && start > end) {
                throw new BadRequestException(
                        "Invalid roll range for room " + entry.room().trim() + ": rollStart (" + start + ") must be <= rollEnd (" + end + ")");
            }
            sanitized.add(new RoomAllocationDto(start, end, entry.room().trim()));
        }
        return sanitized;
    }

    private void checkClockRange(UpsertTermFinalScheduleRequest req) {
        if (!req.examEndTime().isAfter(req.examStartTime())) {
            throw new BadRequestException("Exam end time must be after exam start time");
        }
    }

    private void checkDeptAuthority(User actor, String targetDept) {
        boolean isStaff = actor.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ROLE_ADMIN || r.getName() == RoleName.ROLE_MODERATOR);
        if (isStaff) {
            return;
        }
        boolean isCr = actor.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ROLE_CR);
        if (!isCr) {
            throw new ForbiddenException("Only CR, Moderator, or Admin can manage Term Hub schedules");
        }
        String crDept = normalizeBlank(actor.getDepartment());
        if (crDept == null || !crDept.equalsIgnoreCase(targetDept)) {
            throw new ForbiddenException("CRs can only manage Term Final schedules for their own department");
        }
    }

    private User findActor(Long actorId) {
        return userRepo.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + actorId));
    }

    private static String normalizeBlank(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
