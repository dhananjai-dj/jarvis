package com.project.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class Synthesizer {
    private static final Logger logger = LoggerFactory.getLogger(Synthesizer.class);

    private final File errorMessageFile = new File("error_message.wav");
    private final File waitingMessageFile = new File("waiting_message.wav");

    @Value("${groq.synthesizer.model:}")
    private String model;
    @Value("${groq.synthesizer.endPoint:}")
    private String endPoint;
    @Value("${groq.synthesizer.voice:}")
    private String voice;

    private final RestClient restClient;

    private SourceDataLine speaker;

    private volatile boolean stopRequested = false;

    public Synthesizer(@Qualifier("groqRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void resetStop() {
        this.stopRequested = false;
    }

    public boolean isStopRequested() {
        return this.stopRequested;
    }

    public File synthesizes(String text, String filePath) {
        try {
            Path targetPath = Path.of(filePath, Constants.OUTPUT_RECORDING_FILE_NAME);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Map<String, String> body = new HashMap<>();
            body.put("response_format", "wav");
            body.put("voice", voice);
            body.put("model", model);
            body.put("input", text);
            byte[] audioBytes = restClient.post()
                    .uri(endPoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_OCTET_STREAM, new MediaType("audio", "wav"))
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            if (audioBytes != null && audioBytes.length != 0) {
                Files.write(targetPath, audioBytes);
                return targetPath.toFile();
            }
            logger.error("Got empty file from synthesiser for the content {}", text);
        } catch (Exception e) {
            logger.error("Error in synthesizing {}", e.getMessage());
        }
        return errorMessageFile;
    }

    public void speak(File file) {
        try {
            resetStop();
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file)) {
                AudioFormat audioFormat = audioInputStream.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                speaker = (SourceDataLine) AudioSystem.getLine(info);
                speaker.open(audioFormat);
                speaker.start();

                byte[] buffer = new byte[4096];
                int bytesRead;
                while (!stopRequested && (bytesRead = audioInputStream.read(buffer)) != -1) {
                    speaker.write(buffer, 0, bytesRead);
                }
                if (!stopRequested) {
                    speaker.drain();
                }
                speaker.close();
            }

        } catch (Exception e) {
            logger.error("Error in replying, {}", e.getMessage());
        }
    }

    public void playWaitingMessage() {
        logger.info("Playing waiting message");
        try {
            Thread.sleep(2000);
            if (!isStopRequested()) {
                speak(waitingMessageFile);
            }
        } catch (Exception e) {
            logger.info("Waiting message stopped because of error");
        }
        logger.info("Waiting message stopped");
    }

    public void stopSpeaking() {
        try {
            if (!isStopRequested() && speaker == null) {
                return;
            }
            stopRequested = true;
            logger.info("Stoping");
            speaker.stop();
            speaker.flush();
            speaker.close();
        } catch (Exception e) {
            logger.error("Error in stoping the speech {}", e.getMessage());
        }
    }


}
