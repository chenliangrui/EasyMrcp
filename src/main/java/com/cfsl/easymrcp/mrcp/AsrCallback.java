package com.cfsl.easymrcp.mrcp;

/**
 * asr中识别结果的回调
 * 因为sip、mrcp、asr识别过程是异步的，所以需要在获得asr结果后，手动调用回调函数返回识别结果，
 * 此时mrcp会发送MrcpEventName.RECOGNITION_COMPLETE事件，完成此次asr过程。
 */
public interface AsrCallback {
    /**
     * 获得asr识别结果后回调，执行后mrcp会发送MrcpEventName.RECOGNITION_COMPLETE事件
     *
     * @param action
     * @param msg    asr的识别结果
     */
    void apply(String action, String msg);
}
