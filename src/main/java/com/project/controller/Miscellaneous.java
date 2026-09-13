package com.project.controller;

import com.project.service.Synthesizer;
import com.project.service.Transcriber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
@RequestMapping("/misc")
public class Miscellaneous {

    @Autowired
    Transcriber transcriber;
    @Autowired
    Synthesizer synthesizer;


    @GetMapping("/stt")
    public String getText() throws Exception {
        File file = new File("/Users/dhananjai_dj/dev/jarvis-test/test-recording.wav");
        return transcriber.transcribe(file);
    }

    @GetMapping("/tts")
    public void getSpeech() throws Exception {
       // synthesizer.synthesize("Welcome Boss");
    }

    @GetMapping("/speak")
    public void speak() throws Exception {
       // File wav = synthesizer.synthesize("Please wait While I am looking into it");
       // synthesizer.speak(wav);
    }
}
