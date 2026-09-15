package com.project.controller;

import com.project.service.AiService;
import com.project.util.Synthesizer;
import com.project.util.Transcriber;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/misc")
public class Miscellaneous {

    @Autowired
    Transcriber transcriber;
    @Autowired
    Synthesizer synthesizer;
    @Autowired
    AiService aiService;
    @Autowired
    ToolCallbackProvider provider;


    @GetMapping("/stt")
    public String getText() throws Exception {
        File file = new File("/Users/dhananjai_dj/dev/jarvis-test/test-recording.wav");
        return transcriber.transcribe(file);
    }

    @GetMapping("/speak")
    public void speak() throws Exception {
        File wav = synthesizer.synthesize("Please wait While I am looking into it", "waiting_message.wav");
        synthesizer.speak(wav);
    }

    @GetMapping("/tool")
    public void tool() {
        Set<String> wanted = Set.of("playMusic", "searchSpotify");
        List<ToolCallback> list = Arrays.stream(provider.getToolCallbacks())
                .filter(tc -> wanted.contains(tc.getToolDefinition().name()))
                .toList();
        aiService.performToolCalling("", list);
    }
}
