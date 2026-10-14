package ru.practicum.ewm.stats.mapper;

import ru.practicum.EndpointHitDto;
import ru.practicum.ewm.stats.model.EndpointHit;

public class HitMapper {

    public static EndpointHit toEntity(EndpointHitDto dto) {
        EndpointHit endpoint = new EndpointHit();

        endpoint.setApp(dto.getApp());
        endpoint.setUri(dto.getUri());
        endpoint.setIp(dto.getIp());
        endpoint.setCreated(dto.getTimestamp());
        return endpoint;
    }
}
