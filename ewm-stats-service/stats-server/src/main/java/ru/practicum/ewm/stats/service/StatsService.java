package ru.practicum.ewm.stats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.EndpointHitDto;
import ru.practicum.ViewStatsDto;
import ru.practicum.ewm.stats.mapper.HitMapper;
import ru.practicum.ewm.stats.repository.StatsRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final StatsRepository statsRepository;

    @Transactional
    public void saveHit(EndpointHitDto hit) {
        statsRepository.save(HitMapper.toEntity(hit));
    }

    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
        boolean hasUris = uris != null && !uris.isEmpty();

        if (unique) {
            if (hasUris) {
                return statsRepository.getUniqueStatsByUris(start, end, uris);
            }
            return statsRepository.getUniqueStats(start, end);
        }

        if (hasUris) {
            return statsRepository.getStatsByUris(start, end, uris);
        }
        return statsRepository.getStats(start, end);
    }
}
