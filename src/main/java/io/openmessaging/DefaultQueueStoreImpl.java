package io.openmessaging;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 这是一个简单的基于内存的实现，以方便选手理解题意；
 * 实际提交时，请维持包名和类名不变，把方法实现修改为自己的内容；
 */
public class DefaultQueueStoreImpl extends QueueStore {

    public static final String dir = "/Users/kirito/data/";
//    public static final String dir = "/alidata1/race2018/data/";

    //存储 queue 的索引文件
    Map<String, Index> indexMap = new ConcurrentHashMap<>();
    //存储 queueName 和 queue 编号 的映射关系
    Map<String, Integer> queueNameQueueNoMap = new ConcurrentHashMap<>();
    //存储 (queueNo % CommitLog.commitLogNum) 对应的实际 commitLog
    public Map<Integer, CommitLog> commitLogMap = new ConcurrentHashMap<>();
    //queue计数器
    AtomicInteger queueCnt = new AtomicInteger(0);

    public static Collection<byte[]> EMPTY = new ArrayList<>();

    /**
     * 把一条消息写入一个队列；
     * 这个接口需要是线程安全的，也即评测程序会并发调用该接口进行put；
     * 每个queue中的内容，按发送顺序存储消息（可以理解为Java中的List），同时每个消息会有一个索引，索引从0开始；
     * 不同queue中的内容，相互独立，互不影响；
     *
     * @param queueName 代表queue名字，如果是第一次put，则自动生产一个queue
     * @param message   message，代表消息的内容，评测时内容会随机产生，大部分长度在64字节左右，会有少量消息在1k左右
     */
    @Override
    public void put(String queueName, byte[] message) {
        CommitLog commitLog;
        // TODO 锁 queue
        synchronized (this) {
            Integer queueNo = queueNameQueueNoMap.get(queueName);
            if (queueNo == null) {
                queueNo = queueCnt.incrementAndGet();
                // 新建index
                indexMap.put(queueName, new Index(queueName));
                // 存放queueName和queueNo的关联
                queueNameQueueNoMap.put(queueName, queueNo);
            }
            Integer commitLogId = queueNo % CommitLog.commitLogNum;
            commitLog = commitLogMap.get(commitLogId);
            if (commitLog == null) {
                CommitLog newCommitLog = new CommitLog(commitLogId);
                //新建 commitLog
                commitLogMap.put(commitLogId, newCommitLog);
                commitLog = newCommitLog;
            }
        }

        // 锁的粒度是 commitLog 因为多个 queue 可能对应同一个 commitLog
        synchronized (commitLog) {
            int queueNameLength = queueName.getBytes().length;
            int messageLength = message.length;

            byte[] lengthArray = new byte[2];
            lengthArray[0] = (byte) (queueNameLength & 0xFF);
            lengthArray[1] = (byte) (messageLength & 0xFF);

            int position = commitLog.wrotePosition.get();

            byte[] composeMessage = new byte[queueNameLength + messageLength + 2];

            System.arraycopy(lengthArray, 0, composeMessage, 0, lengthArray.length);
            System.arraycopy(queueName.getBytes(), 0, composeMessage, lengthArray.length, queueName.getBytes().length);
            System.arraycopy(message, 0, composeMessage, lengthArray.length + queueName.getBytes().length, message.length);

            commitLog.appendMessage(composeMessage);

            //TODO 需要定时或定量刷盘防止内存溢出

            //建立索引
            Index index = indexMap.get(queueName);
            ByteBuffer slice = index.mappedByteBuffer.slice();
            slice.position(index.IndexWrotePosition.get());
            slice.putInt(position);
            index.IndexWrotePosition.addAndGet(4);
            //TODO 需要定时或定量刷盘防止内存溢出
//        index.writeBuffer.force();
        }

    }

    /**
     * 从一个队列中读出一批消息，读出的消息要按照发送顺序来；
     * 这个接口需要是线程安全的，也即评测程序会并发调用该接口进行get；
     * 返回的Collection会被并发读，但不涉及写，因此只需要是线程读安全就可以了；
     *
     * @param queueName 代表队列的名字
     * @param offset    代表消息的在这个队列中的起始消息索引
     * @param num       代表读取的消息的条数，如果消息足够，则返回num条，否则只返回已有的消息即可;没有消息了，则返回一个空的集合
     */
    @Override
    public Collection<byte[]> get(String queueName, long offset, long num) {

        List<byte[]> list = new ArrayList<>();
        if (!indexMap.containsKey(queueName)) {
            return EMPTY;
        }

        Index index = indexMap.get(queueName);
        int wrotePosition = index.IndexWrotePosition.get();

        // slice 独立管理读写指针所以不需要加锁
        ByteBuffer indexByteBuffer = index.mappedByteBuffer.slice();
        int commitLogNo = queueNameQueueNoMap.get(queueName) % CommitLog.commitLogNum;
        CommitLog commitLog = commitLogMap.get(commitLogNo);
        // 计算最大的可读数据量
        // 一个索引占 4 字节
        num = Math.min((wrotePosition / 4) - offset, num);
        for (long i = 0; i < num; i++) {
            indexByteBuffer.position((int) ((offset + i) * 4));
            int pos = indexByteBuffer.getInt();
            if (pos == Integer.MAX_VALUE) {
                break;
            }
            byte[] bytes = commitLog.readMessage(pos);
            if (bytes == null) {
                break;
            }
            list.add(bytes);
        }
        return list;
    }

}
