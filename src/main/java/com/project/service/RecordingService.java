package com.project.service;

import com.project.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.File;

@Service
public class RecordingService {

    private static final Logger logger = LoggerFactory.getLogger(RecordingService.class);

    private Thread captureThread;
    private TargetDataLine microphone;
    private volatile boolean recording = false;


    private File outputFile = null;

    public synchronized void startRecording(String filePath) {
        try {
            if (recording) {
                throw new IllegalStateException("Already recording");
            }
            outputFile = new File(filePath + Constants.INPUT_RECORDING_FILE_NAME);
            outputFile.getParentFile().mkdirs();
            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();
            recording = true;
            captureThread = new Thread(() -> {
                try (AudioInputStream audioStream = new AudioInputStream(microphone)) {
                    AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputFile);
                } catch (Exception e) {
                    logger.error("Error in writing the audio into the file {}", e.getMessage());
                }
            });
            captureThread.start();
        } catch (Exception e) {
            logger.error("Error in starting the recording {}", e.getMessage());
        }
    }

    public synchronized File stopRecording() {
        try {
            if (!recording) {
                throw new IllegalStateException("Not currently recording");
            }
            microphone.stop();
            microphone.close();
            recording = false;
            captureThread.join();
        } catch (Exception e) {
            logger.error("Error in stoping the Recording {}", e.getMessage());
        }
        return outputFile;
    }
}
