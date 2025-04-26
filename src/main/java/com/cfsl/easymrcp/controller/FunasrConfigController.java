package com.cfsl.easymrcp.controller;

import com.cfsl.easymrcp.asr.funasr.FunasrConfig;
import com.cfsl.easymrcp.controller.vo.FunasrConfigVo;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/funasr-config")
public class FunasrConfigController {
    private final FunasrConfig funasrConfig;

    public FunasrConfigController(FunasrConfig funasrConfig) {
        this.funasrConfig = funasrConfig;
    }

    @GetMapping
    public FunasrConfig getConfig() {
        FunasrConfig funasrConfigVo = new FunasrConfig();
        BeanUtils.copyProperties(funasrConfig, funasrConfigVo);
        return funasrConfigVo;
    }

    @PutMapping
    public FunasrConfigVo updateConfig(@RequestBody FunasrConfigVo newConfig) {
        System.out.println(newConfig.toString());
        return newConfig;
    }
}
