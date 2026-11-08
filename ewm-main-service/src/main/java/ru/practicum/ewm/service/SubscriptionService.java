package ru.practicum.ewm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.UserShortDto;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.UserMapper;
import ru.practicum.ewm.model.Subscription;
import ru.practicum.ewm.model.User;
import ru.practicum.ewm.repository.SubscriptionRepository;
import ru.practicum.ewm.repository.UserRepository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EventService eventService;

    @Transactional
    public UserShortDto subscribe(Long userId, Long followingId) {
        if (userId.equals(followingId)) {
            throw new ConflictException("Cannot subscribe to yourself");
        }
        User follower = getUser(userId);
        User following = getUser(followingId);
        if (subscriptionRepository.existsByFollowerIdAndFollowingId(userId, followingId)) {
            throw new ConflictException("Subscription already exists");
        }
        Subscription subscription = new Subscription();
        subscription.setFollower(follower);
        subscription.setFollowing(following);
        subscriptionRepository.save(subscription);
        return UserMapper.toUserShortDto(following);
    }

    @Transactional
    public void unsubscribe(Long userId, Long followingId) {
        getUser(userId);
        Subscription subscription = subscriptionRepository.findByFollowerIdAndFollowingId(userId, followingId)
                .orElseThrow(() -> new NotFoundException("Subscription was not found"));
        subscriptionRepository.delete(subscription);
    }

    public List<UserShortDto> getSubscriptions(Long userId, int from, int size) {
        getUser(userId);
        return subscriptionRepository.findFollowing(userId, PageRequest.of(from / size, size)).stream()
                .map(UserMapper::toUserShortDto)
                .collect(Collectors.toList());
    }

    public List<EventShortDto> getEvents(Long userId, int from, int size) {
        getUser(userId);
        List<Long> followingIds = subscriptionRepository.findFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return Collections.emptyList();
        }
        return eventService.getPublishedEventsByUsers(followingIds, from, size);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id=" + userId + " was not found"));
    }
}
