package com.ecibet.game5inline.cache;

import com.ecibet.game5inline.model.Lobby;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class ActiveLobbiesCache {

    private final Cache<String, Lobby> lobbyCache;
    private final Cache<String, String> codeToIdCache;

    public ActiveLobbiesCache() {
        this.lobbyCache = Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .maximumSize(1000)
                .build();

        this.codeToIdCache = Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.HOURS)
                .maximumSize(1000)
                .build();
    }

    public void put(Lobby lobby) {
        lobbyCache.put(lobby.getId(), lobby);
        codeToIdCache.put(lobby.getCode(), lobby.getId());
    }

    public Optional<Lobby> getById(String lobbyId) {
        return Optional.ofNullable(lobbyCache.getIfPresent(lobbyId));
    }

    public Optional<Lobby> getByCode(String code) {
        String lobbyId = codeToIdCache.getIfPresent(code);
        if (lobbyId == null) {
            return Optional.empty();
        }
        return getById(lobbyId);
    }

    public void remove(String lobbyId) {
        Lobby lobby = lobbyCache.getIfPresent(lobbyId);
        if (lobby != null) {
            codeToIdCache.invalidate(lobby.getCode());
            lobbyCache.invalidate(lobbyId);
        }
    }

    public Collection<Lobby> getAll() {
        return lobbyCache.asMap().values();
    }

    public boolean existsByCode(String code) {
        return codeToIdCache.getIfPresent(code) != null;
    }
}