package com.flux.fluxproject.schedulers;

import com.flux.fluxproject.services.X.SchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
@Component
public class PostScheduler {

    private final SchedulingService schedulingService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay = 30000)
    public void checkDuePosts(){
        if (!running.compareAndSet(false, true)){
            log.info("Scheduler already running, skipping this tick");
            return;
        }
        schedulingService.executePosting(15)
                .doFinally(sig->running.set(false))
                .subscribe();

    }

}
