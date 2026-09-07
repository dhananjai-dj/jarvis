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
    private final MicRecorderService micRecorderService;

    private volatile boolean recording = false;

    public KeyboardListener(Orchestrator orchestrator, MicRecorderService micRecorderService) {
        this.orchestrator = orchestrator;
        this.micRecorderService = micRecorderService;
    }

    @Override
    public void run(String @NonNull ... args) throws Exception {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(java.util.logging.Level.WARNING);
        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
        logger.info("Press and hold s to record");
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent event) {
        if (event.getKeyCode() == NativeKeyEvent.VC_ALT && event.getKeyLocation() == NativeKeyEvent.KEY_LOCATION_RIGHT && !recording) {
            try {
                logger.info("Recording started");
                orchestrator.stopSpeech();
                micRecorderService.startRecording();
                recording = true;
            } catch (Exception e) {
                logger.error("Error in starting the recording {}", e.getMessage());
            }
        } else if (event.getKeyCode() == NativeKeyEvent.VC_CONTROL && event.getKeyLocation() == NativeKeyEvent.KEY_LOCATION_RIGHT && !recording) {
            orchestrator.stopSpeech();
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent event) {
        if (event.getKeyCode() == NativeKeyEvent.VC_ALT && event.getKeyLocation() == NativeKeyEvent.KEY_LOCATION_RIGHT && recording) {
            try {
                logger.info("Recording stopped. Transcribing...");
                var wav = micRecorderService.stopRecording();
                recording = false;
                new Thread(() -> orchestrator.respond(wav)).start();
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