package com.cfsl.easymrcp.rtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * G.711A编码解码工具类
 */
public class G711AUtil {
    private static short aLawDecompressTable[] = new short[]
            { -5504, -5248, -6016, -5760, -4480, -4224, -4992, -4736, -7552, -7296, -8064, -7808, -6528, -6272, -7040, -6784, -2752, -2624, -3008, -2880, -2240, -2112, -2496, -2368, -3776, -3648, -4032, -3904, -3264, -3136, -3520, -3392, -22016, -20992, -24064, -23040, -17920, -16896, -19968, -18944, -30208, -29184, -32256, -31232, -26112, -25088, -28160, -27136, -11008, -10496, -12032, -11520, -8960, -8448, -9984, -9472, -15104, -14592, -16128, -15616, -13056, -12544, -14080, -13568, -344, -328, -376,
                    -360, -280, -264, -312, -296, -472, -456, -504, -488, -408, -392, -440, -424, -88, -72, -120, -104, -24, -8, -56, -40, -216, -200, -248, -232, -152, -136, -184, -168, -1376, -1312, -1504, -1440, -1120, -1056, -1248, -1184, -1888, -1824, -2016, -1952, -1632, -1568, -1760, -1696, -688, -656, -752, -720, -560, -528, -624, -592, -944, -912, -1008, -976, -816, -784, -880, -848, 5504, 5248, 6016, 5760, 4480, 4224, 4992, 4736, 7552, 7296, 8064, 7808, 6528, 6272, 7040, 6784, 2752, 2624,
                    3008, 2880, 2240, 2112, 2496, 2368, 3776, 3648, 4032, 3904, 3264, 3136, 3520, 3392, 22016, 20992, 24064, 23040, 17920, 16896, 19968, 18944, 30208, 29184, 32256, 31232, 26112, 25088, 28160, 27136, 11008, 10496, 12032, 11520, 8960, 8448, 9984, 9472, 15104, 14592, 16128, 15616, 13056, 12544, 14080, 13568, 344, 328, 376, 360, 280, 264, 312, 296, 472, 456, 504, 488, 408, 392, 440, 424, 88, 72, 120, 104, 24, 8, 56, 40, 216, 200, 248, 232, 152, 136, 184, 168, 1376, 1312, 1504, 1440, 1120,
                    1056, 1248, 1184, 1888, 1824, 2016, 1952, 1632, 1568, 1760, 1696, 688, 656, 752, 720, 560, 528, 624, 592, 944, 912, 1008, 976, 816, 784, 880, 848 };

    private final static int cClip = 32635;
    private static byte aLawCompressTable[] = new byte[]
            { 1, 1, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7 };

    /**
     * 编码  pcm  to  G711 a-law
     * @param b
     * @return
     */
    public static byte[] encode( byte[] b){
        int j = 0;
        int count = b.length / 2;
        short sample = 0;
        byte[] res = new byte[count];
        for ( int i = 0; i < count; i++ )
        {
            sample = (short) ( ( ( b[j++] & 0xff ) | ( b[j++] ) << 8 ) );
            res[i] = linearToALawSample( sample );
        }
        return res;
    }

    /**
     * 编码 PCM to G711 a-law (ByteBuf版本，避免内存拷贝)
     * @param input PCM数据的ByteBuf
     * @return G711编码后的ByteBuf
     */
    public static ByteBuf encode(ByteBuf input) {
        if (input == null || input.readableBytes() == 0) {
            return ByteBufAllocator.DEFAULT.buffer(0);
        }
        
        int count = input.readableBytes() / 2;
        ByteBuf output = ByteBufAllocator.DEFAULT.buffer(count);
        
        for (int i = 0; i < count; i++) {
            // 保持与旧版一致的字节序：小端序读取
            byte lowByte = input.readByte();
            byte highByte = input.readByte();
            short sample = (short) ((lowByte & 0xff) | (highByte << 8));
            output.writeByte(linearToALawSample(sample));
        }
        
        return output;
    }


    /**
     * 解码
     * @param b
     * @return
     */
    public static byte[] decode( byte[] b){
        int j = 0;
        byte[] res = new byte[b.length*2];
        for ( int i = 0; i < b.length; i++ )
        {
            short s = aLawDecompressTable[b[i] & 0xff];
            res[j++] = (byte) s;
            res[j++] = (byte) ( s >> 8 );
        }
        return res;
    }

    /**
     * 解码 G711 a-law to PCM (ByteBuf版本，避免内存拷贝)
     * @param input G711编码数据的ByteBuf
     * @return PCM解码后的ByteBuf
     */
    public static ByteBuf decode(ByteBuf input) {
        if (input == null || input.readableBytes() == 0) {
            return ByteBufAllocator.DEFAULT.buffer(0);
        }
        
        int length = input.readableBytes();
        ByteBuf output = ByteBufAllocator.DEFAULT.buffer(length * 2);
        
        for (int i = 0; i < length; i++) {
            short s = aLawDecompressTable[input.readByte() & 0xff];
            // 保持与旧版一致的字节序：小端序写入
            output.writeByte((byte) s);
            output.writeByte((byte) (s >> 8));
        }
        
        return output;
    }


    private static byte linearToALawSample( short sample ){
        int sign;
        int exponent;
        int mantissa;
        int s;

        sign = ( ( ~sample ) >> 8 ) & 0x80;
        if ( !( sign == 0x80 ) )
        {
            sample = (short) -sample;
        }
        if ( sample > cClip )
        {
            sample = cClip;
        }
        if ( sample >= 256 )
        {
            exponent = (int) aLawCompressTable[( sample >> 8 ) & 0x7F];
            mantissa = ( sample >> ( exponent + 3 ) ) & 0x0F;
            s = ( exponent << 4 ) | mantissa;
        }
        else
        {
            s = sample >> 4;
        }
        s ^= ( sign ^ 0x55 );
        return (byte) s;
    }

    static byte[] g711aDecode(byte[] alawData) {
        byte[] pcm = new byte[alawData.length * 2];

        for (int i = 0, j = 0; i < alawData.length; i++, j += 2) {
            short sample = aLawToLinear(alawData[i]);
            pcm[j] = (byte) (sample & 0xFF);
            pcm[j + 1] = (byte) (sample >> 8);
        }
        return pcm;
    }

    private static short aLawToLinear(byte alaw) {
        alaw ^= 0xD5;
        int sign = alaw & 0x80;
        int exponent = (alaw & 0x70) >> 4;
        int mantissa = alaw & 0x0F;

        int magnitude = (mantissa << 4) + 0x08;
        if (exponent > 0) magnitude += 0x100;
        if (exponent > 1) magnitude <<= (exponent - 1);

        return (short) (sign == 0 ? magnitude : -magnitude);
    }

}
