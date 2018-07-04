package io.openmessaging;

import org.junit.Test;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 徐靖峰
 * Date 2018-06-23
 */
public class MmapTest {

    public static void ensureDirOK(final String dirName) {
        if (dirName != null) {
            File f = new File(dirName);
            if (!f.exists()) {
                boolean result = f.mkdirs();
                System.out.println((dirName + " mkdir " + (result ? "OK" : "Failed")));
            }
        }
    }

    private final static int _1K = 1 * 1024;
    private final static int _1M = 1 * 1024 * 1024;
    private final static int _1G = 1 * 1024 * 1024 * 1024;
    private final static int _10G = 10 * 1024 * 1024 * 1024;

    private static final String[] quotes = {
            "Where there is love there is life.\n",
            "First they ignore you, then they laugh at you, then they fight you, then you win.\n",
            "Be the change you want to see in the world.\n",
            "The weak can never forgive. Forgiveness is the attribute of the strong.\n",
    };

    @Test
    public void test1() throws Exception {
        String dir = "/Users/kirito/alibaba/queuerace2018/data/";
        ensureDirOK(dir);
        RandomAccessFile memoryMappedFile = new RandomAccessFile(dir + "test3.txt", "rw");

        long fileSize = _10G;

        Random random = new Random();

        // Mapping a file into memory
        MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, fileSize);

        // Writing into Memory Mapped File
        while (true) {
            byte[] readyToWrite = quotes[random.nextInt(quotes.length)].getBytes();
            if (out.remaining() > readyToWrite.length + 4) {
                out.putInt(readyToWrite.length);
                out.put(readyToWrite);
            } else {
                break;
            }
        }
        System.out.println("Writing to Memory Mapped File is completed");

        memoryMappedFile.close();
    }

    @Test
    public void test2() throws Exception {
        String dir = "/Users/kirito/alibaba/queuerace2018/data/";
        ensureDirOK(dir);
        RandomAccessFile memoryMappedFile = new RandomAccessFile(dir + "test3.txt", "rw");

        // Mapping a file into memory
        MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, _10G);
//        MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, memoryMappedFile.length());

        while (out.hasRemaining()) {
            if (out.remaining() < 4 + 1) {
                return;
            }
            int length = out.getInt();
            byte[] memo;
            if (out.remaining() < length) {
                memo = new byte[out.remaining()];
                out.get(memo, 0, out.remaining());
            } else {
                memo = new byte[length];
                out.get(memo, 0, length);
            }
            System.out.println(new String(memo));
        }
        memoryMappedFile.close();
    }

    @Test
    public void test3() throws Exception {
        String dir = "/Users/kirito/alibaba/data/";
        ensureDirOK(dir);
        RandomAccessFile memoryMappedFile = new RandomAccessFile(dir + "test3.txt", "rw");

        // Mapping a file into memory
        for (int i = 0; i < 200; i++) {
            MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, _1G);
            System.out.println(i);
        }

        memoryMappedFile.close();
    }

    /**
     * 测试mmap写100个文件
     *
     * @throws Exception
     */
    @Test
    public void test4() throws Exception {
        String dir = "/Users/kirito/data/";
        ensureDirOK(dir);

        int n = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch countDownLatch = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int no = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    RandomAccessFile memoryMappedFile = null;
                    try {
                        memoryMappedFile = new RandomAccessFile(dir + "test" + no + ".txt", "rw");
                        long fileSize = _1M;

                        Random random = new Random();

                        MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, fileSize);

                        while (true) {
                            byte[] readyToWrite = quotes[random.nextInt(quotes.length)].getBytes();
                            if (out.remaining() > readyToWrite.length + 4) {
                                out.putInt(readyToWrite.length);
                                out.put(readyToWrite);
                            } else {
                                break;
                            }
                        }
                        out.force();
//                        memoryMappedFile.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
    }

    /**
     * 测试MappedByteBuffer写入一个大文件
     *
     * @throws Exception
     */
    @Test
    public void test5() throws Exception {
        String dir = "/Users/kirito/data/";
        ensureDirOK(dir);

        int n = 200;
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch countDownLatch = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int no = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    RandomAccessFile memoryMappedFile;
                    try {
                        memoryMappedFile = new RandomAccessFile(dir + "big.txt", "rw");
                        long fileSize = _1G;

                        Random random = new Random();

                        MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, no * fileSize, fileSize);

                        while (true) {
                            byte[] readyToWrite = quotes[random.nextInt(quotes.length)].getBytes();
                            if (out.remaining() > readyToWrite.length + 4) {
                                out.putInt(readyToWrite.length);
                                out.put(readyToWrite);
                            } else {
                                break;
                            }
                        }
                        //有本事去掉这个
//                        out.force();
                        memoryMappedFile.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
    }

    /**
     * 测试 fileChannel 写入
     *
     * @throws Exception
     */
    @Test
    public void test6() throws Exception {
        String dir = "/Users/kirito/data/";
        ensureDirOK(dir);

        int n = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch countDownLatch = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int no = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    RandomAccessFile memoryMappedFile = null;
                    try {
                        memoryMappedFile = new RandomAccessFile(dir + "test" + no + ".txt", "rw");
                        long fileSize = _1G;

                        AtomicInteger wrotePosition = new AtomicInteger(0);

                        Random random = new Random();

                        FileChannel channel = memoryMappedFile.getChannel();

                        while (true) {
                            byte[] readyToWrite = quotes[random.nextInt(quotes.length)].getBytes();
                            if (4 + readyToWrite.length + wrotePosition.get() > fileSize) {
                                break;
                            }
                            ByteBuffer lengthByteBuffer = ByteBuffer.allocate(4).putInt(readyToWrite.length);
                            lengthByteBuffer.flip();
                            channel.write(lengthByteBuffer, wrotePosition.get());
                            ByteBuffer readyToWriteByteBuffer = ByteBuffer.allocate(readyToWrite.length).put(readyToWrite);
                            readyToWriteByteBuffer.flip();
                            channel.write(readyToWriteByteBuffer, wrotePosition.get() + 4);
                            wrotePosition.addAndGet(4 + readyToWrite.length);
                        }
                        channel.force(false);
                        memoryMappedFile.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
    }

    /**
     * 测试4k数据量的写入
     *
     * @throws Exception
     */
    @Test
    public void test7() throws Exception {
        String dir = "/Users/kirito/data/";
        ensureDirOK(dir);

        byte[] _4K = new byte[4 * 1024];
        for (int i = 0; i < _4K.length; i++) {
            _4K[i] = 'a';
        }

        int n = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch countDownLatch = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int no = i;
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    RandomAccessFile memoryMappedFile = null;
                    try {
                        memoryMappedFile = new RandomAccessFile(dir + "test" + no + ".txt", "rw");
                        long fileSize = _1G;

                        AtomicInteger wrotePosition = new AtomicInteger(0);

                        Random random = new Random();

                        FileChannel channel = memoryMappedFile.getChannel();

                        while (true) {
                            if (wrotePosition.get() + _4K.length > fileSize) {
                                break;
                            }
                            channel.write(ByteBuffer.wrap(_4K));
                            wrotePosition.addAndGet(_4K.length);
                        }
                        channel.force(false);
                        memoryMappedFile.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
    }


    @Test
    public void test8() throws Exception {
        String dir = "/Users/kirito/data/";
        ensureDirOK(dir);
        RandomAccessFile memoryMappedFile = null;
        try {
            memoryMappedFile = new RandomAccessFile(dir + "test.txt", "rw");
            long fileSize = _1K;

            AtomicInteger wrotePosition = new AtomicInteger(0);

            MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            int a = 0;
            while (true) {

                if (wrotePosition.get() + 4 > fileSize) {
                    break;
                }
                out.putInt(a++);
                wrotePosition.addAndGet(4);
            }
            out.force();
            memoryMappedFile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test9() throws Exception {
        String dir = "/Users/kirito/data/";
        ensureDirOK(dir);
        RandomAccessFile memoryMappedFile = null;
        int size = 4 * 100000;
        try {
            memoryMappedFile = new RandomAccessFile(dir + "intTest.txt", "rw");
            MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, size);
            for (int i = 0; i < 100000 -1; i++) {
                out.position(i * 4);
                out.putInt(i);
            }
            out.force();
            memoryMappedFile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test10() throws Exception {
        String dir = "/Users/kirito/data/";
        ensureDirOK(dir);
        RandomAccessFile memoryMappedFile = null;
        int size = 4 * 100000;
        try {
            memoryMappedFile = new RandomAccessFile(dir + "intTest.txt", "rw");
            MappedByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, size);
            for (int i = 0; i < 100000; i++) {
                out.position(i * 4);
                int anInt = out.getInt();
                System.out.println(anInt);
            }
            memoryMappedFile.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
