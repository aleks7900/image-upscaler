package com.upscaler.service;

import com.upscaler.dto.ProgressEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SseProgressService {

    private final Map<UUID, List<SseEmitter>> emittersByBatch = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID batchId) {
        // 30-minute timeout for large batch processing
        SseEmitter emitter = new SseEmitter(1800_000L);

        emittersByBatch.computeIfAbsent(batchId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(batchId, emitter));
        emitter.onTimeout(() -> removeEmitter(batchId, emitter));
        emitter.onError(e -> removeEmitter(batchId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data(Map.of("message", "Connected to progress stream for batch " + batchId)));
        } catch (IOException e) {
            removeEmitter(batchId, emitter);
        }

        return emitter;
    }

    public void broadcastProgress(UUID batchId, ProgressEventDto progress) {
        List<SseEmitter> emitters = emittersByBatch.get(batchId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("PROGRESS")
                        .data(progress));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
        if (emitters.isEmpty()) {
            emittersByBatch.remove(batchId);
        }
    }

    public void broadcastComplete(UUID batchId, ProgressEventDto progress) {
        List<SseEmitter> emitters = emittersByBatch.get(batchId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("COMPLETED")
                        .data(progress));
                emitter.complete();
            } catch (Exception ignored) {}
        }
        emittersByBatch.remove(batchId);
    }

    private void removeEmitter(UUID batchId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByBatch.get(batchId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByBatch.remove(batchId);
            }
        }
    }
}
