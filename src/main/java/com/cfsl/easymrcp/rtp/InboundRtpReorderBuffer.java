package com.cfsl.easymrcp.rtp;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
public final class InboundRtpReorderBuffer {
    private static final int MAX_SEQUENCE = 0x10000;
    private static final long MAX_TIMESTAMP = 0x1_0000_0000L;

    // 当前 RTP 流的 payload type，补静音包时沿用它。
    private final int payloadType;
    // 默认 payload 大小，补静音包长度会用到。
    private final int defaultPayloadSize;
    // 后视窗口深度。
    // 1. 启动前至少先攒这么多包再开始放；
    // 2. expectedSeq 缺失时，后面至少看到这么多个更大的 seq，才判这个 seq 丢了。
    private final int reorderWindowPackets;
    // 连续补静音次数上限，超过后不再硬补，直接把 expectedSeq 同步到当前最早可用包。
    private final int maxConsecutiveLossFill;
    // 单包 timestamp 步长，按协商好的 ptime 固定计算，补静音包时直接使用它。
    private final long timestampStep;
    // 预先构造好的静音 payload，补包时直接复用。
    private final byte[] silencePayload;
    // 当前窗口里暂存、但还没释放的 RTP 包，key 是 sequence number。
    private final Map<Integer, RtpPacket> bufferedPackets = new HashMap<>();

    // 是否已经完成启动预热并确定了 expectedSequenceNumber。
    private boolean started;
    // 当前希望输出的下一个 seq。
    // 这是整个重排状态机推进的主轴：有它就直接放，没有它才判断是否丢包或 resync。
    private int expectedSequenceNumber;
    // 下面几个是运行期统计。
    private int duplicateCount;
    private int lateCount;
    private int reorderedCount;
    private int lossFillCount;
    // 当前连续补静音了多少次，用来判断是否触发 resync。
    private int consecutiveLossFillCount;
    // 最近一次输出的包，可能是真实包，也可能是补出来的静音包。
    private RtpPacket lastEmittedPacket;

    public InboundRtpReorderBuffer(int payloadType,
                                   int defaultPayloadSize,
                                   int reorderWindowPackets,
                                   int maxConsecutiveLossFill,
                                   long timestampStep) {
        if (payloadType < 0 || payloadType > 127) {
            throw new IllegalArgumentException("payloadType must be between 0 and 127");
        }
        if (defaultPayloadSize < 0) {
            throw new IllegalArgumentException("defaultPayloadSize must be non-negative");
        }
        if (reorderWindowPackets < 0) {
            throw new IllegalArgumentException("reorderWindowPackets must be non-negative");
        }
        if (maxConsecutiveLossFill < 0) {
            throw new IllegalArgumentException("maxConsecutiveLossFill must be non-negative");
        }
        if (timestampStep < 0) {
            throw new IllegalArgumentException("timestampStep must be non-negative");
        }
        this.payloadType = payloadType;
        this.defaultPayloadSize = defaultPayloadSize;
        this.reorderWindowPackets = reorderWindowPackets;
        this.maxConsecutiveLossFill = maxConsecutiveLossFill;
        this.timestampStep = timestampStep;
        this.silencePayload = createSilencePayload(payloadType, defaultPayloadSize);
    }

    /**
     * 收到一个新的 RTP 包后推进窗口状态。
     * 处理顺序为：晚到/重复丢弃 -> 放入窗口 -> 缓满后启动 -> 围绕 expectedSeq 连续释放。
     */
    public void offer(RtpPacket packet, Consumer<RtpPacket> consumer) {
        Objects.requireNonNull(packet, "packet must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");
        int sequenceNumber = packet.getSequenceNumber();
        if (started && compareSequence(sequenceNumber, expectedSequenceNumber) < 0) {
            lateCount++;
            log.info("RTP重排丢弃晚到包: seq={}, expectedSeq={}", sequenceNumber, expectedSequenceNumber);
            return;
        }
        if (bufferedPackets.containsKey(sequenceNumber)) {
            duplicateCount++;
            log.info("RTP重排丢弃重复包: seq={}, expectedSeq={}", sequenceNumber, expectedSequenceNumber);
            return;
        }
        if (started && compareSequence(sequenceNumber, expectedSequenceNumber) > 0) {
            reorderedCount++;
        }

        bufferedPackets.put(sequenceNumber, packet);
        if (!started && bufferedPackets.size() >= startDepthPackets()) {
            expectedSequenceNumber = findEarliestBufferedSequence();
            started = true;
        }

        drain(consumer);
    }

    /**
     * 清空当前窗口状态。
     * pause/close 或配置变更后重新开始收包时使用。
     */
    public void reset() {
        started = false;
        expectedSequenceNumber = 0;
        bufferedPackets.clear();
        duplicateCount = 0;
        lateCount = 0;
        reorderedCount = 0;
        lossFillCount = 0;
        consecutiveLossFillCount = 0;
        lastEmittedPacket = null;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public int getLateCount() {
        return lateCount;
    }

    public int getReorderedCount() {
        return reorderedCount;
    }

    public int getLossFillCount() {
        return lossFillCount;
    }

    public int getBufferedPacketCount() {
        return bufferedPackets.size();
    }

    /**
     * 从 expectedSeq 开始尽可能连续向前推进：
     * 1. 有 expectedSeq 就直接输出；
     * 2. 没有 expectedSeq，但它后面已经积累够窗口深度，就判定当前 expectedSeq 丢失；
     * 3. 连续补静音超预算后，直接把 expectedSeq 同步到当前窗口里最早可用的包。
     */
    private void drain(Consumer<RtpPacket> consumer) {
        if (!started) {
            return;
        }

        while (true) {
            RtpPacket packet = bufferedPackets.remove(expectedSequenceNumber);
            if (packet != null) {
                emitPacket(packet, consumer);
                continue;
            }

            if (bufferedPackets.isEmpty()) {
                return;
            }

            int packetsAhead = bufferedPackets.size();
            if (packetsAhead < reorderWindowPackets) {
                log.info("RTP重排等待缺失包: expectedSeq={}, packetsAhead={}, reorderWindowPackets={}",
                        expectedSequenceNumber, packetsAhead, reorderWindowPackets);
                return;
            }
            if (consecutiveLossFillCount >= maxConsecutiveLossFill) {
                if (!resyncToEarliestBuffered()) {
                    return;
                }
                continue;
            }

            emitSilencePacket(consumer);
        }
    }

    /**
     * 连续补静音超过预算时，直接把 expectedSeq 跳到窗口里当前最早可释放的包。
     */
    private boolean resyncToEarliestBuffered() {
        if (bufferedPackets.isEmpty()) {
            return false;
        }
        consecutiveLossFillCount = 0;
        expectedSequenceNumber = findEarliestBufferedSequence();
        return true;
    }

    /**
     * 输出一个真实 RTP 包，并把 expectedSeq 推进到下一个序号。
     */
    private void emitPacket(RtpPacket packet, Consumer<RtpPacket> consumer) {
        consumer.accept(packet);
        lastEmittedPacket = packet;
        consecutiveLossFillCount = 0;
        expectedSequenceNumber = nextSequence(packet.getSequenceNumber());
    }

    /**
     * 当前缺失的 expectedSeq 被判定为丢包时，补一个静音包占位并继续向前推进。
     */
    private void emitSilencePacket(Consumer<RtpPacket> consumer) {
        RtpPacket silencePacket = createSilencePacket();
        consumer.accept(silencePacket);
        lastEmittedPacket = silencePacket;
        expectedSequenceNumber = nextSequence(expectedSequenceNumber);
        lossFillCount++;
        consecutiveLossFillCount++;
    }

    /**
     * 窗口至少要缓存多少包后才开始释放。
     * 配置为 0 时仍至少缓存 1 包，避免未见任何包时启动。
     */
    private int startDepthPackets() {
        return Math.max(reorderWindowPackets, 1);
    }

    /*
     * 统计 expectedSeq 后面已经看到了多少个更大的序号。
     * 当这个数量达到窗口深度时，认为当前 expectedSeq 大概率不会再来了。
     */

    /**
     * 在当前窗口里找到逻辑上最早的那个序号，作为启动后的 expectedSeq。
     */
    private int findEarliestBufferedSequence() {
        Integer earliest = null;
        for (Integer candidate : bufferedPackets.keySet()) {
            if (earliest == null || compareSequence(candidate, earliest) < 0) {
                earliest = candidate;
            }
        }
        if (earliest == null) {
            throw new IllegalStateException("bufferedPackets must not be empty when starting");
        }
        return earliest;
    }

    /**
     * 根据固定 timestampStep 生成补静音包的时间戳。
     */
    private RtpPacket createSilencePacket() {
        long timestamp = 0L;
        if (lastEmittedPacket != null) {
            timestamp = (lastEmittedPacket.getTimestamp() + timestampStep) % MAX_TIMESTAMP;
            return RtpPacket.silenceLike(lastEmittedPacket, expectedSequenceNumber, timestamp, silencePayload);
        }
        return RtpPacket.of(payloadType, expectedSequenceNumber, timestamp, silencePayload);
    }

    /**
     * 在当前窗口里找到 expectedSeq 后面距离最近的真实包。
     * 这个包用于补静音时间戳推算以及 resync 目标选择。
     */

    /**
     * 比较两个 16 bit RTP 序号的前后关系。
     * 输入:
     * left/right 都是 0~65535 的 RTP sequence number。
     * 输出:
     * 小于 0 表示 left 比 right 早；
     * 等于 0 表示两者相同；
     * 大于 0 表示 left 比 right 晚。
     * 这里按 RTP 序号回绕后的逻辑顺序比较，不是普通的整数大小比较。
     */
    private static int compareSequence(int left, int right) {
        int delta = (left - right) & 0xFFFF;
        if (delta >= 0x8000) {
            delta -= MAX_SEQUENCE;
        }
        return Integer.compare(delta, 0);
    }

    private static int nextSequence(int sequenceNumber) {
        return (sequenceNumber + 1) & 0xFFFF;
    }

    private static byte[] createSilencePayload(int payloadType, int payloadSize) {
        byte fillByte;
        if (payloadType == AudioCodecUtil.PT_PCMU) {
            fillByte = (byte) 0xFF;
        } else if (payloadType == AudioCodecUtil.PT_PCMA) {
            fillByte = (byte) 0xD5;
        } else {
            fillByte = 0x00;
        }
        byte[] payload = new byte[payloadSize];
        Arrays.fill(payload, fillByte);
        return payload;
    }
}
