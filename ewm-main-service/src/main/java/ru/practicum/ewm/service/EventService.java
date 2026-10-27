package ru.practicum.ewm.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.EndpointHitDto;
import ru.practicum.StatsClient;
import ru.practicum.ViewStatsDto;
import ru.practicum.ewm.dto.EventFullDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.Location;
import ru.practicum.ewm.dto.NewEventDto;
import ru.practicum.ewm.dto.UpdateEventAdminRequest;
import ru.practicum.ewm.dto.UpdateEventUserRequest;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.AdminStateAction;
import ru.practicum.ewm.model.Category;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.model.EventSort;
import ru.practicum.ewm.model.EventState;
import ru.practicum.ewm.model.RequestStatus;
import ru.practicum.ewm.model.User;
import ru.practicum.ewm.model.UserStateAction;
import ru.practicum.ewm.repository.CategoryRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.repository.RequestRepository;
import ru.practicum.ewm.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final StatsClient statsClient;

    @Transactional
    public EventFullDto create(Long userId, NewEventDto dto) {
        checkEventDate(dto.getEventDate(), 2);
        Event event = EventMapper.toEvent(dto, getCategory(dto.getCategory()), getUser(userId));
        event.setCreatedOn(LocalDateTime.now());
        event.setState(EventState.PENDING);
        if (event.getPaid() == null) {
            event.setPaid(false);
        }
        if (event.getParticipantLimit() == null) {
            event.setParticipantLimit(0);
        }
        if (event.getRequestModeration() == null) {
            event.setRequestModeration(true);
        }
        event = eventRepository.save(event);
        return EventMapper.toFullDto(event, 0, 0);
    }

    public List<EventShortDto> getUserEvents(Long userId, int from, int size) {
        List<Event> events = eventRepository.findAllByInitiatorId(userId, PageRequest.of(from / size, size))
                .getContent();
        return toShortDtos(events);
    }

    public EventFullDto getUserEvent(Long userId, Long eventId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        return EventMapper.toFullDto(event, getConfirmed(eventId), getViews(eventId));
    }

    @Transactional
    public EventFullDto updateByUser(Long userId, Long eventId, UpdateEventUserRequest dto) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Cannot update published event");
        }
        patchEvent(event, dto.getAnnotation(), dto.getCategory(), dto.getDescription(),
                dto.getEventDate(), dto.getLocation(), dto.getPaid(), dto.getParticipantLimit(),
                dto.getRequestModeration(), dto.getTitle(), 2L);
        if (dto.getStateAction() == UserStateAction.SEND_TO_REVIEW) {
            event.setState(EventState.PENDING);
        }
        if (dto.getStateAction() == UserStateAction.CANCEL_REVIEW) {
            event.setState(EventState.CANCELED);
        }
        return EventMapper.toFullDto(event, getConfirmed(eventId), getViews(eventId));
    }

    public List<EventFullDto> getAdminEvents(List<Long> users, List<EventState> states, List<Long> categories,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                             int from, int size) {
        checkRange(rangeStart, rangeEnd);
        List<Event> events = eventRepository.findAdminEvents(
                emptyToNull(users), emptyToNull(states), emptyToNull(categories),
                rangeStart, rangeEnd, PageRequest.of(from / size, size)).getContent();
        return toFullDtos(events);
    }

    @Transactional
    public EventFullDto updateByAdmin(Long eventId, UpdateEventAdminRequest dto) {
        Event event = getEvent(eventId);
        patchEvent(event, dto.getAnnotation(), dto.getCategory(), dto.getDescription(),
                dto.getEventDate(), dto.getLocation(), dto.getPaid(), dto.getParticipantLimit(),
                dto.getRequestModeration(), dto.getTitle(), null);
        if (dto.getStateAction() == AdminStateAction.PUBLISH_EVENT) {
            if (event.getState() != EventState.PENDING) {
                throw new ConflictException("Cannot publish event not in pending state");
            }
            if (event.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ConflictException("Cannot publish event starting in less than 1 hour");
            }
            event.setState(EventState.PUBLISHED);
            event.setPublishedOn(LocalDateTime.now());
        }
        if (dto.getStateAction() == AdminStateAction.REJECT_EVENT) {
            if (event.getState() == EventState.PUBLISHED) {
                throw new ConflictException("Cannot reject published event");
            }
            event.setState(EventState.CANCELED);
        }
        return EventMapper.toFullDto(event, getConfirmed(eventId), getViews(eventId));
    }

    public List<EventShortDto> getPublicEvents(String text, List<Long> categories, Boolean paid,
                                               LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                               boolean onlyAvailable, EventSort sort,
                                               int from, int size, HttpServletRequest request) {
        checkRange(rangeStart, rangeEnd);
        sendHit(request);
        LocalDateTime start = rangeStart != null ? rangeStart : LocalDateTime.now();
        List<Event> events = eventRepository.findPublicEvents(
                EventState.PUBLISHED, text, emptyToNull(categories), paid, start, rangeEnd);
        List<EventShortDto> result = toShortDtos(events);
        if (onlyAvailable) {
            Map<Long, Event> byId = events.stream().collect(Collectors.toMap(Event::getId, event -> event));
            result = result.stream()
                    .filter(dto -> {
                        Event event = byId.get(dto.getId());
                        return event.getParticipantLimit() == 0
                                || dto.getConfirmedRequests() < event.getParticipantLimit();
                    })
                    .collect(Collectors.toList());
        }
        if (sort == EventSort.VIEWS) {
            result.sort(Comparator.comparing(EventShortDto::getViews).reversed());
        } else {
            result.sort(Comparator.comparing(EventShortDto::getEventDate));
        }
        if (from >= result.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(from + size, result.size());
        return new ArrayList<>(result.subList(from, to));
    }

    public EventFullDto getPublicEvent(Long eventId, HttpServletRequest request) {
        Event event = getEvent(eventId);
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event with id=" + eventId + " was not found");
        }
        sendHit(request);
        return EventMapper.toFullDto(event, getConfirmed(eventId), getViews(eventId));
    }

    Map<Long, Long> getConfirmedMap(List<Long> eventIds) {
        Map<Long, Long> result = new HashMap<>();
        if (eventIds.isEmpty()) {
            return result;
        }
        for (Object[] row : requestRepository.countByEventIdsAndStatus(eventIds, RequestStatus.CONFIRMED)) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }

    Map<Long, Long> getViewsMap(List<Long> eventIds) {
        Map<Long, Long> result = new HashMap<>();
        if (eventIds.isEmpty()) {
            return result;
        }
        List<String> uris = eventIds.stream()
                .map(id -> "/events/" + id)
                .collect(Collectors.toList());
        try {
            List<ViewStatsDto> stats = statsClient.getViewStats(
                    LocalDateTime.of(1970, 1, 1, 0, 0),
                    LocalDateTime.now().plusYears(100),
                    uris,
                    false);
            for (ViewStatsDto stat : stats) {
                String uri = stat.getUri();
                Long id = Long.parseLong(uri.substring(uri.lastIndexOf('/') + 1));
                result.put(id, stat.getHits());
            }
        } catch (Exception e) {
            return result;
        }
        return result;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }

    private Category getCategory(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }

    private Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private void checkEventDate(LocalDateTime eventDate, long hours) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(hours))) {
            throw new ConflictException("Event date is too soon");
        }
    }

    private void patchEvent(Event event, String annotation, Long categoryId, String description,
                            LocalDateTime eventDate, Location location, Boolean paid,
                            Integer participantLimit, Boolean requestModeration, String title,
                            Long hoursBefore) {
        if (annotation != null) {
            event.setAnnotation(annotation);
        }
        if (categoryId != null) {
            event.setCategory(getCategory(categoryId));
        }
        if (description != null) {
            event.setDescription(description);
        }
        if (eventDate != null) {
            if (hoursBefore != null) {
                checkEventDate(eventDate, hoursBefore);
            }
            event.setEventDate(eventDate);
        }
        if (location != null) {
            event.setLat(location.getLat());
            event.setLon(location.getLon());
        }
        if (paid != null) {
            event.setPaid(paid);
        }
        if (participantLimit != null) {
            event.setParticipantLimit(participantLimit);
        }
        if (requestModeration != null) {
            event.setRequestModeration(requestModeration);
        }
        if (title != null) {
            event.setTitle(title);
        }
    }

    private long getConfirmed(Long eventId) {
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    private long getViews(Long eventId) {
        Map<Long, Long> views = getViewsMap(List.of(eventId));
        return views.getOrDefault(eventId, 0L);
    }

    private void sendHit(HttpServletRequest request) {
        EndpointHitDto hit = new EndpointHitDto();
        hit.setApp("ewm-main-service");
        hit.setUri(request.getRequestURI());
        hit.setIp(request.getRemoteAddr());
        hit.setTimestamp(LocalDateTime.now());
        try {
            statsClient.setHit(hit);
        } catch (Exception e) {
            return;
        }
    }

    private void checkRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new ValidationException("rangeStart is after rangeEnd");
        }
    }

    private <T> List<T> emptyToNull(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list;
    }

    private List<EventShortDto> toShortDtos(List<Event> events) {
        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmed = getConfirmedMap(ids);
        Map<Long, Long> views = getViewsMap(ids);
        return events.stream()
                .map(event -> EventMapper.toShortDto(
                        event,
                        confirmed.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private List<EventFullDto> toFullDtos(List<Event> events) {
        List<Long> ids = events.stream().map(Event::getId).collect(Collectors.toList());
        Map<Long, Long> confirmed = getConfirmedMap(ids);
        Map<Long, Long> views = getViewsMap(ids);
        return events.stream()
                .map(event -> EventMapper.toFullDto(
                        event,
                        confirmed.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
    }
}
