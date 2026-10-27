package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.CompilationDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.NewCompilationDto;
import ru.practicum.ewm.dto.UpdateCompilationRequest;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CompilationMapper;
import ru.practicum.ewm.mapper.EventMapper;
import ru.practicum.ewm.model.Compilation;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.repository.CompilationRepository;
import ru.practicum.ewm.repository.EventRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;

    @Transactional
    public CompilationDto create(NewCompilationDto dto) {
        Compilation compilation = new Compilation();
        compilation.setTitle(dto.getTitle());
        compilation.setPinned(dto.getPinned() != null ? dto.getPinned() : false);
        compilation.setEvents(new HashSet<>(getEvents(dto.getEvents())));
        Compilation saved = compilationRepository.save(compilation);
        List<Long> eventIds = saved.getEvents().stream().map(Event::getId).collect(Collectors.toList());
        return toDto(saved, eventService.getConfirmedMap(eventIds), eventService.getViewsMap(eventIds));
    }

    @Transactional
    public CompilationDto update(Long compId, UpdateCompilationRequest dto) {
        Compilation compilation = getOrThrow(compId);
        if (dto.getTitle() != null) {
            compilation.setTitle(dto.getTitle());
        }
        if (dto.getPinned() != null) {
            compilation.setPinned(dto.getPinned());
        }
        if (dto.getEvents() != null) {
            compilation.setEvents(new HashSet<>(getEvents(dto.getEvents())));
        }
        List<Long> eventIds = compilation.getEvents() == null
                ? Collections.emptyList()
                : compilation.getEvents().stream().map(Event::getId).collect(Collectors.toList());
        return toDto(compilation, eventService.getConfirmedMap(eventIds), eventService.getViewsMap(eventIds));
    }

    @Transactional
    public void delete(Long compId) {
        getOrThrow(compId);
        compilationRepository.deleteById(compId);
    }

    public List<CompilationDto> getAll(Boolean pinned, int from, int size) {
        PageRequest page = PageRequest.of(from / size, size);
        List<Compilation> compilations;
        if (pinned == null) {
            compilations = compilationRepository.findAllWithEvents(page).getContent();
        } else {
            compilations = compilationRepository.findAllByPinned(pinned, page).getContent();
        }
        List<Long> eventIds = compilations.stream()
                .filter(compilation -> compilation.getEvents() != null)
                .flatMap(compilation -> compilation.getEvents().stream())
                .map(Event::getId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Long> confirmed = eventService.getConfirmedMap(eventIds);
        Map<Long, Long> views = eventService.getViewsMap(eventIds);
        return compilations.stream()
                .map(compilation -> toDto(compilation, confirmed, views))
                .collect(Collectors.toList());
    }

    public CompilationDto getById(Long compId) {
        Compilation compilation = getOrThrow(compId);
        List<Long> eventIds = compilation.getEvents() == null
                ? Collections.emptyList()
                : compilation.getEvents().stream().map(Event::getId).collect(Collectors.toList());
        return toDto(compilation, eventService.getConfirmedMap(eventIds), eventService.getViewsMap(eventIds));
    }

    private Compilation getOrThrow(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
    }

    private List<Event> getEvents(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return eventRepository.findAllByIdIn(ids);
    }

    private CompilationDto toDto(Compilation compilation, Map<Long, Long> confirmed, Map<Long, Long> views) {
        List<Event> events = compilation.getEvents() == null
                ? Collections.emptyList()
                : new ArrayList<>(compilation.getEvents());
        List<EventShortDto> shortDtos = events.stream()
                .map(event -> EventMapper.toShortDto(
                        event,
                        confirmed.getOrDefault(event.getId(), 0L),
                        views.getOrDefault(event.getId(), 0L)))
                .collect(Collectors.toList());
        return CompilationMapper.toDto(compilation, shortDtos);
    }
}
