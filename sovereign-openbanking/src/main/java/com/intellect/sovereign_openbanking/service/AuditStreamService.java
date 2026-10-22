package com.intellect.sovereign.service;

import com.intellect.sovereign.model.DecisionResult.AgentAuditTrace;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AuditStreamService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(180000L); // 3 minutes timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcastTrace(AgentAuditTrace trace) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("agent-trace").data(trace));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}