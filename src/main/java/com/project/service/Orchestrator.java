package com.project.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class Orchestrator {
    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);

    private final Transcriber transcriber;
    private final ChatService chatService;
    private final Synthesizer synthesizer;


    public Orchestrator(Transcriber transcriber, ChatService chatService, Synthesizer synthesizer) {
        this.transcriber = transcriber;
        this.chatService = chatService;
        this.synthesizer = synthesizer;
    }

    public void respond(File wav) {
        synthesizer.resetStop();
        Thread waitingThread = new Thread(synthesizer::playWaitingMessage);
        waitingThread.start();
        try {

            String text = transcriber.transcribe(wav);
            logger.info("Transcript: {}", text);

            String result = chatService.search(text);
            logger.info("Output from ml: {}", result);

            synthesizer.stopSpeaking();
            waitingThread.join();

            logger.info("Responding to the question now");
            File outputFile = synthesizer.synthesize(result);
            synthesizer.speak(outputFile);
        } catch (Exception e) {
            logger.error("Error in responding {}", e.getMessage());
        }
    }

    public void stopSpeech() {
        synthesizer.stopSpeaking();
    }
}
