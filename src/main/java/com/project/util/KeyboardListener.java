package com.project.util;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.project.service.Orchestrator;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@Service
public class KeyboardListener implements CommandLineRunner, NativeKeyListener {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardListener.class);

    private final Orchestrator orchestrator;

    private final AtomicBoolean recording = new AtomicBoolean();
    private final AtomicBoolean isNewSession = new AtomicBoolean();
    private final AtomicInteger count = new AtomicInteger(0);
    private String sessionId = null;

    public KeyboardListener(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(String @NonNull ... args) throws Exception {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(java.util.logging.Level.WARNING);
        isNewSession.set(true);
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
        logger.info("Press and hold s to record");
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent event) {
        if (event.getKeyCode() == NativeKeyEvent.VC_ALT && event.getKeyLocation() == NativeKeyEvent.KEY_LOCATION_RIGHT && !recording.get()) {
            if (isNewSession.get()) {
                sessionId = String.valueOf(System.currentTimeMillis());
            }
            count.set(count.get() + 1);
            try {
                logger.info("Recording started");
                orchestrator.stopSpeech();
                orchestrator.startRecording(sessionId, count.get());
                recording.set(true);
            } catch (Exception e) {
                logger.error("Error in starting the recording {}", e.getMessage());
            }
        } else if (event.getKeyCode() == NativeKeyEvent.VC_ESCAPE && !recording.get() && !isNewSession.get()) {
            orchestrator.saveSummary(sessionId);
            orchestrator.stopSpeech();
            isNewSession.set(true);
            sessionId = null;
            count.set(0);
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent event) {
        if (event.getKeyCode() == NativeKeyEvent.VC_ALT && event.getKeyLocation() == NativeKeyEvent.KEY_LOCATION_RIGHT && recording.get()) {
            try {
                logger.info("Recording stopped. Transcribing...");
                var wav = orchestrator.stopRecording(sessionId, count.get());
                boolean sessionState = isNewSession.getAndSet(false);
                new Thread(() -> orchestrator.respond(wav, sessionId, Constants.RECORDING_PATH_PREFIX + sessionId + "/" + count.get(), sessionState)).start();
                recording.set(false);
            } catch (Exception e) {
                logger.error("Error in stopping the recording {}", e.getMessage());
            }
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // required by the interface, not needed for our use case
    }
}