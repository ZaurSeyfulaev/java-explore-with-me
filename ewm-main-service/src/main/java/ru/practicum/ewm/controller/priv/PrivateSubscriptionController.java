package ru.practicum.ewm.controller.priv;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.UserShortDto;
import ru.practicum.ewm.service.SubscriptionService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/subscriptions")
public class PrivateSubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/{followingId}")
    @ResponseStatus(HttpStatus.CREATED)
    public UserShortDto subscribe(@PathVariable Long userId, @PathVariable Long followingId) {
        return subscriptionService.subscribe(userId, followingId);
    }

    @DeleteMapping("/{followingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@PathVariable Long userId, @PathVariable Long followingId) {
        subscriptionService.unsubscribe(userId, followingId);
    }

    @GetMapping
    public List<UserShortDto> getSubscriptions(@PathVariable Long userId,
                                               @RequestParam(defaultValue = "0") int from,
                                               @RequestParam(defaultValue = "10") int size) {
        return subscriptionService.getSubscriptions(userId, from, size);
    }

    @GetMapping("/events")
    public List<EventShortDto> getEvents(@PathVariable Long userId,
                                         @RequestParam(defaultValue = "0") int from,
                                         @RequestParam(defaultValue = "10") int size) {
        return subscriptionService.getEvents(userId, from, size);
    }
}
