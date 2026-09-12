package com.project.service;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;


@Service
public class KeyboardListener implements CommandLineRunner, NativeKeyListener {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardListener.class);

    private final Orchestrator orchestrator;
    private final AudioRecorderService audioRecorderService;

    private volatile boolean recording = false;
    private volatile boolean isNewSession = false;
    private volatile String sessionId = null;

    public KeyboardListener(Orchestrator orchestrator, AudioRecorderService audioRecorderService) {
        this.orchestrator = orchestrator;
        this.audioRecorderService = audioRecorderService;
    }

    @Override
    public void run(String @NonNull ... args) throws Exception {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(java.util.logging.Level.WARNING);
        sessionId = String.valueOf(System.currentTimeMillis());
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
        logger.info("Press and hold s to record");
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent event) {
        if (event.getKeyCode() == NativeKeyEvent.VC_ALT && event.getKeyLocation() == NativeKeyEvent.KEY_LOCATION_RIGHT && !recording) {
            if (!isNewSession) {
                sessionId = String.valueOf(System.currentTimeMillis());
                isNewSession = true;
            } else {
                isNewSession = false;
            }
            try {
                logger.info("Recording started");
                orchestrator.stopSpeech();
                audioRecorderService.startRecording();
                recording = true;
            } catch (Exception e) {
                logger.error("Error in starting the recording {}", e.getMessage());
            }
        } else if (event.getKeyCode() == NativeKeyEvent.VC_ESCAPE && !recording) {
            orchestrator.saveSummary(sessionId);
            orchestrator.stopSpeech();
            sessionId = null;
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent event) {
        if (event.getKeyCode() == NativeKeyEvent.VC_ALT && event.getKeyLocation() == NativeKeyEvent.KEY_LOCATION_RIGHT && recording) {
            try {
                logger.info("Recording stopped. Transcribing...");
                var wav = audioRecorderService.stopRecording();
                recording = false;
                new Thread(() -> orchestrator.respond(wav, sessionId, isNewSession)).start();
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