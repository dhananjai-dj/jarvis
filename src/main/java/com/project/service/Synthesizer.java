package com.project.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

@Service
public class Synthesizer {
    private static final Logger logger = LoggerFactory.getLogger(Synthesizer.class);

    private final File waitMessageFile = new File("waiting_message.wav");
    private static final String PIPER_PATH = "/Users/dhananjai_dj/dev/piper-venv/bin/piper";
    private static final String MODEL_PATH = "/Users/dhananjai_dj/dev/piper/voices/en_US-ryan-medium.onnx";
    private SourceDataLine speaker;

    private volatile boolean stopRequested = false;

    public void resetStop() {
        this.stopRequested = false;
    }

    public boolean isStopRequested() {
        return this.stopRequested;
    }

    public File synthesize(String text) throws IOException, InterruptedException {
        File outputFile = new File("tts-output.wav");
        ProcessBuilder processBuilder = new ProcessBuilder(
                PIPER_PATH,
                "--model", MODEL_PATH,
                "--output_file", outputFile.getAbsolutePath()
        );
        processBuilder.redirectErrorStream(false);
        Process process = processBuilder.start();

        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(text.getBytes());
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Piper process failed with exit code " + exitCode);
        }

        return outputFile;
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

    public void playWaitingMessage(){
        logger.info("Playing waiting message");
        try {
            while (!isStopRequested()) {
                speak(waitMessageFile);
                Thread.sleep(2000);
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
