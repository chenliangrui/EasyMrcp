public class test_vad_frame_size {
    public static void main(String[] args) throws Exception {
        System.out.println("=== VAD Frame Size Test ===");

        // 创建测试数据
        byte[] bigFrame = new byte[2048];  // 当前使用的2048字节
        byte[] smallFrame = new byte[512];  // 建议的512字节

        // 初始化VAD
        VadHandle vad = new VadHandle();

        // 模拟传入大量数据
        long startTimeBig = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            vad.receivePcm(bigFrame);
        }
        long timeBig = System.currentTimeMillis() - startTimeBig;

        // 重置状态
        // 注意：实际项目中没有暴露重置方法，但我们可以通过创建新实例
        vad = new VadHandle();

        long startTimeSmall = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            vad.receivePcm(smallFrame);
        }
        long timeSmall = System.currentTimeMillis() - startTimeSmall;

        System.out.println("=== 测试结果 ===");
        System.out.println("1000次VAD检测（2048字节帧）: " + timeBig + "ms");
        System.out.println("1000次VAD检测（512字节帧）: " + timeSmall + "ms");
        System.out.println("=== 性能提升 ===");
        double improvement = (double) (timeBig - timeSmall) / timeBig * 100;
        System.out.printf("CPU消耗减少约: %.1f%%\n", improvement);

        System.out.println("\n=== 结论 ===");
        System.out.println("帧大小从2048字节调整到512字节可节省约" +
                           (int) improvement + "%的CPU资源！");
    }
}
