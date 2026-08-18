package com.cfsl.easymrcp.controller;

import com.cfsl.easymrcp.service.tts.OfflineTtsSynthesisService;
import com.cfsl.easymrcp.service.tts.TtsSynthesizeRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 提供发布阶段使用的 TTS 合成接口，不参与实时通话播放。
 */
@RestController
@RequestMapping("/api/tts")
public class TtsSynthesizeController {
    private final OfflineTtsSynthesisService synthesisService;

    public TtsSynthesizeController(OfflineTtsSynthesisService synthesisService) {
        this.synthesisService = synthesisService;
    }

    @PostMapping(value = "/synthesize", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody TtsSynthesizeRequest request) {
        byte[] wav = synthesisService.synthesize(request);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/wav"));
        headers.setContentLength(wav.length);
        headers.setContentDisposition(ContentDisposition.inline().filename("speak.wav").build());
        return ResponseEntity.ok().headers(headers).body(wav);
    }
}
