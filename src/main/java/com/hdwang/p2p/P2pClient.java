package com.hdwang.p2p;

import com.alibaba.fastjson.JSONObject;
import com.hdwang.udp.Udp;
import com.hdwang.utils.IpUtil;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Created by hdwang on 2017-03-26.
 * p2p客户端
 */
public class P2pClient {
    /**
     * 日志
     */
    private final static Logger logger = LoggerFactory.getLogger(P2pClient.class);

    /**
     * 服务器相关地址
     */
    private String ip = "127.0.0.1";
    private int port;
    private int anotherPort;

    /**
     * 客户端相关地址
     */
    private String clientIp = "127.0.0.1";
    private int clientPort;
    private String clientId;
    private String remoteClientId;
    private String remotePublicIp;
    private int remotePublicPort;

    /**
     * 缓冲区大小
     */
    private int max_packet_size;
    private int socketTimeout = 2000;
    /**
     * Udp操作对象
     */
    private  Udp client;

    /**
     * 客户端socket
     */
    private DatagramSocket clientSocket;

    /**
     * 是否已经发送过这个消息
     */
    private boolean haveSendMsg_p2p_connect_try_3 = false;

    /**
     * 是否是请求方
     */
    private boolean isToRequest;

    /**
     * 是否已经建立p2p连接
     */
    private volatile boolean haveConnected = false;

    //心跳配置
    private int heartbeatInterval=1000;
    private boolean heartbeatShowMsg= false;
    private List<Thread> heartbeatThread = new ArrayList<Thread>();
    private Object lock1 = new Object();
    private Object lock2 = new Object();
    private Object lock3 = new Object();
    private boolean suspendHeartbeat = false; //设置是否需要挂起
    private boolean suspendReceiveMsg = false; //设置是否需要挂起
    private boolean haveSuspendReceiveMsg = false; //是否已经挂起

    private boolean beginToSendFile = false;

    private String fileName;
    private int packetSize;
    private String dir="C:\\";
    private FileOutputStream fos;
    private List<Integer> haveSavedFilePacketNumber = new ArrayList<>(); //已经收到的文件包序号，做幂等控制

    /**
     * 构造函数
     * @param configuration 配置信息
     */
    public P2pClient(Configuration configuration){
        ip = configuration.getString("server.ip");
        port = configuration.getInt("server.port");
        anotherPort = 18000;  //configuration.getInt("server.anotherPort");

        clientIp = configuration.getString("client.ip");
        clientPort = configuration.getInt("client.port");
        clientId = configuration.getString("client.id");
        remoteClientId = configuration.getString("remoteClient.id");

        isToRequest = configuration.getString("isToRequest").equals("y")?true:false;

        heartbeatInterval = configuration.getInt("heartbeat.interval");
        heartbeatShowMsg = configuration.getBoolean("heartbeat.showmsg");

        max_packet_size = configuration.getInt("packet.size");
        dir = configuration.getString("file.save.dir");
    }

    /**
     * 开始跑
     */
    public void run(){

        //1.check this client if support the p2p
        logger.info("check this client if support the p2p...");
        isSupportP2pCheck();

        //2.try to make connection
        logger.info("try to make connection...");
        boolean isConnected = tryToConnectRemoteClient();

        //3.begin send and receive information
        if(isConnected){
            logger.info("与对方建立起了p2p连接，现在你可以放心发消息了！");
            //3.1 开始接收消息、文件等
            this.receiveMsg();

            //3.2 发送消息、文件
            this.sendMsg();

        }else{
            logger.error("对不起，无法建立与远程客户端的P2P连接！");
        }
    }

    /**
     * check this client if support the p2p
     * @return if this client support the p2p
     */
    private boolean isSupportP2pCheck(){
        this.client = new Udp(clientPort); //绑定端口号
        clientSocket= client.init();
        //String localIp = clientSocket.getLocalAddress().getHostAddress();
        List<String> localIps =IpUtil.getAllLocalIpList();
        int localPort = clientSocket.getLocalPort();
        logger.info(String.format("your pc local address is %s:%s", Arrays.toString(IpUtil.getAllLocalIp()),localPort));

        //发两次包，检测是否支持p2p
        byte[] connectMsgBytes = Udp.addHeadToPacket("", Udp.Tag.p2p_support_check, 1);
        client.sendPacket(clientSocket,connectMsgBytes, ip, port); //给s1+port1 发送第一个包，类型是p2p_support_check
        client.sendPacket(clientSocket,connectMsgBytes, ip, port+1); //给s1+port2 发送第一个包，类型是p2p_support_check

        //接收结果
        DatagramPacket packet = client.getPacket(clientSocket, max_packet_size, socketTimeout);
        String line1 = client.getPacketStr(packet,false);
        String[] ipPort1 = line1.split(":");
        String publicIp1 = ipPort1[0];
        int publicPort1 = Integer.parseInt(ipPort1[1]);
        logger.info(String.format("your pc public address is %s:%s",publicIp1,publicPort1));

        //比较本地ip和public ip是否一致，一致说明本机在公网
        if(localIps.contains(publicIp1) && localPort == publicPort1){
            logger.info("congratulation! your pc support the p2p connection!(public ip)");
            replyIsSupportP2p(true);
            return true;
        }

        DatagramPacket packet2 = client.getPacket(clientSocket, max_packet_size, socketTimeout);
        String line2 = client.getPacketStr(packet2,false);
        String[] ipPort2 = line2.split(":");
        String publicIp2 = ipPort2[0];
        int publicPort2 = Integer.parseInt(ipPort2[1]);
        logger.info(String.format("your pc public address is %s:%s",publicIp2,publicPort2));

        //比较两次出网ip+port是否一致，一致则为cone nat，支持p2p
        if(publicIp1.equals(publicIp2) && publicPort1 == publicPort2){
            logger.info("congratulation! your pc support the p2p connection!(cone nat)");

            //答复自己的p2p是否可行
            replyIsSupportP2p(true);
            return true;
        }

        logger.info("sorry, your pc doesn't support the p2p connection!(symmetrical nat)");
        //答复自己的p2p是否可行
        replyIsSupportP2p(false);
        return false;
    }

    /**
     * 答复服务器自己的p2p是否可以连接
     * @param isSupport 是否支持p2p
     */
    private void replyIsSupportP2p(boolean isSupport) {
        String isSupportMsg = "n";
        if(isSupport){
            isSupportMsg = "y";
        }

        //答复自己的p2p是否可行

        String reply = String.format("{id:\"%s\",isSupport:\"%s\"}", this.clientId, isSupportMsg);
        byte[] replyBytes = Udp.addHeadToPacket(reply, Udp.Tag.p2p_support_check, 2);
        client.sendPacket(clientSocket,replyBytes,ip,port);
    }

    /**
     * 尝试建立p2p连接
     * @return 是否建立起了p2p连接
     */
    private boolean tryToConnectRemoteClient() {
        boolean isConnected = false;

        //1.请求与另一个客户端建立连接
        logger.info("请求与另一个客户端建立连接");
        if(isToRequest){ //请求端
            //发送建立p2p连接请求
            logger.info("发送建立p2p连接请求");
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id",this.clientId);
            jsonObject.put("remoteId",this.remoteClientId);
            byte[] requestBytes = Udp.addHeadToPacket(jsonObject.toJSONString(), Udp.Tag.p2p_connect_try, 1);
            client.sendPacket(clientSocket,requestBytes,ip,port);
        }else{ //被请求端

        }

        //2.获取请求结果和对方公网ip
        logger.info("获取请求结果和对方公网ip");
        DatagramPacket packet = client.getPacket(clientSocket, max_packet_size, 10000);
        String receiveMsg = client.getPacketStr(packet,false);
        JSONObject jsonObject= JSONObject.parseObject(receiveMsg);
        final String clientId = jsonObject.getString("id");
        String remoteId = jsonObject.getString("remoteId");
        boolean support = jsonObject.getString("support").equals("y")?true:false;
        remotePublicIp = jsonObject.getString("remotePublicIp");
        remotePublicPort = jsonObject.getInteger("remotePublicPort");
        if(support){
            logger.info("support p2p connection, try to make a connection...");

            //3.循环打洞，直至成功建立连接
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while(true){
                        byte[] makeHole = Udp.addHeadToPacket("make hole...",Udp.Tag.p2p_connect_try,2);
                        client.sendPacket(clientSocket,makeHole,remotePublicIp,remotePublicPort);
                        logger.info(String.format("make hole with address:%s:%s",remotePublicIp,remotePublicPort));
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
//                            logger.info("停止打洞");
                            break; //被打断
                        }
                    }
                }
            });
            thread.start();

            //4.判断自己是否收到消息，收到便可以停止打洞
            Thread thread2 = new Thread(new Runnable() {
                @Override
                public void run() {
                    int i = 0; //收到消息的次数
                    while(true){
                        DatagramPacket packet = client.getPacket(clientSocket, max_packet_size, socketTimeout);
                        String receiveMsg = client.getPacketStr(packet,true);
                        String head = Udp.getHeadFromPacket(receiveMsg,Udp.Tag.p2p_connect_try);
                        if(head.equals(Udp.getHead(Udp.Tag.p2p_connect_try,2))){ //收到打洞消息
                            thread.interrupt();  //停止打洞
                            while(!thread.isAlive()){
                                //等待打洞线程终止
                            }
                            logger.info("I have received make hole msg! stop make hole.");
                            byte[] haveGotMsg = Udp.addHeadToPacket("haveGotMsg",Udp.Tag.p2p_connect_try,3);
                            client.sendPacket(clientSocket,haveGotMsg,remotePublicIp,remotePublicPort); //告知对方已收到消息
                            logger.info("tell remote I have got msg!");
                            haveSendMsg_p2p_connect_try_3 = true;
                        }else  if(head.equals(Udp.getHead(Udp.Tag.p2p_connect_try,3))){
                            //收到对方成功接收消息的答复（udp包无顺序，p2p_connect_try_3可能比p2p_connect_try_2先到达）
                            if(haveSendMsg_p2p_connect_try_3==false){ //没有告知对方
                                if(!thread.isInterrupted()){
                                    thread.interrupt();
                                    while(!thread.isAlive()){
                                        //等待打洞线程终止
                                    }
                                }
                                byte[] haveGotMsg = Udp.addHeadToPacket("haveGotMsg",Udp.Tag.p2p_connect_try,3);
                                client.sendPacket(clientSocket,haveGotMsg,remotePublicIp,remotePublicPort); //告知对方已收到消息
                                haveSendMsg_p2p_connect_try_3 = true;
                            }
                            haveConnected = true;
                            logger.info("congratulation! p2p connection is bulid successful!");
                            break; //退出循环
                        }
                    }
                }
            });
            thread2.start();

            //5.等待建立好p2p连接
            try {
                thread2.join();
            } catch (InterruptedException e) {
                logger.error("wait to build a p2p connection failed!",e);
            }

            //6.保持udp连接
            keepConnection();

            isConnected = true;
        }
        return isConnected;
    }

    /**
     * 保持udp连接（发送心跳信息，因为一段时间不发送消息，p2p连接就断开了）
     */
    private void keepConnection() {
        Thread sendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    synchronized (lock1){ //获得同步锁
                        if(suspendHeartbeat){ //挂起当前线程
                            try {
                                logger.info("heartbeat send msg wait...");
                                lock1.wait();
                            } catch (InterruptedException e) {
                                logger.info("心跳等待被打断");
                            }
                        }
                    }
                    byte[] heartbeatMsg = Udp.addHeadToPacket("", Udp.Tag.p2p_connect_heartbeat, 1);
                    client.sendPacket(clientSocket,heartbeatMsg,remotePublicIp,remotePublicPort);
                    try {
                        Thread.sleep(P2pClient.this.heartbeatInterval);
                    } catch (InterruptedException e) {
                        logger.info("心跳睡眠被打断");
//                       Thread.currentThread().resume();

                    }
                }
            }
        });
        sendThread.start();

//       Thread receiveThread =  new Thread(new Runnable() {
//            @Override
//            public void run() {
//                int i = 0; //收到消息的次数
//                while (!Thread.interrupted()) {
//                    synchronized (lock2){ //获得同步锁
//                        if(suspendHeartbeat){ //挂起当前线程
//                            try {
//                                logger.info("heartbeat receive msg wait...");
//                                lock2.wait();
//                            } catch (InterruptedException e) {
//                                logger.info("心跳等待被打断");
//                            }
//                        }
//                    }
//                    String receiveMsg = client.getPacketStr(clientSocket, max_packet_size);
//                    String head = Udp.getHeadFromPacket(receiveMsg, Udp.Tag.p2p_connect_heartbeat);
//                    if (head.equals(Udp.getHead(Udp.Tag.p2p_connect_heartbeat, 1))) {
//                        //心跳消息
//                        if (heartbeatShowMsg) {
//                            if (i++ % 10 == 0) {
//                                System.out.println("...heartbeat msg...");
//                            }
//                        }
//                    }
//                }
//            }
//        });
//        receiveThread.start();
    }

    /**
     * 挂起或者唤醒心跳线程
     */
    private void setSuspendHeartbeat(boolean suspend){
        if (!suspend) {
            synchronized (lock1) {
                lock1.notify();
            }
            synchronized (lock2){
                lock2.notify();
            }
        }
        this.suspendHeartbeat = suspend;
    }

    /**
     * 挂起接收消息
     * @param suspend
     */
    private void setSuspendReceiveMsg(boolean suspend){
        if (!suspend) {
            synchronized (lock3){
                lock3.notify();
            }
        }
        this.suspendReceiveMsg = suspend;
    }

    /**
     * 接收消息、文件
     */
    private void receiveMsg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    synchronized (lock3){
                        if(suspendReceiveMsg){
                            try {
                                logger.info("receive msg wait...");
                                haveSuspendReceiveMsg = true;
                                lock3.wait();
                            } catch (InterruptedException e) {
                                logger.info("接收消息等待被打断");
                            }
                        }
                    }
                    haveSuspendReceiveMsg = false;
                    DatagramPacket packet = client.getPacket(clientSocket,max_packet_size,10000);
                    byte[] data = packet.getData();
                    String tag = Udp.getTagBytesFromPacket(data);
                    if(Udp.Tag.p2p_send_msg.equals(tag)){ //接收消息
                        String receiveMsg  = client.getPacketStr(packet,true);
                        String head = Udp.getHeadFromPacket(receiveMsg, Udp.Tag.p2p_send_msg);
                        if(head.equals(Udp.getHead(Udp.Tag.p2p_send_msg,1))){ // 正式的通信
                            String lineBody = Udp.removeHeadFromPacket(receiveMsg,Udp.Tag.p2p_send_msg);
                            System.out.println("remote say:" + lineBody); //输出消息
                        }
                    }else if(Udp.Tag.p2p_send_file_info.equals(tag)){ //接收文件信息
                        receiveFileInfo(packet);
                    }else if(Udp.Tag.p2p_send_file.equals(tag)){ //接收文件
                        receiveFile(data);
                    }
                }
            }
        }).start();
    }

    /**
     * 接收文件信息
     * @param packet 数据包
     */
    private void receiveFileInfo(DatagramPacket packet) {
        String receiveMsg = client.getPacketStr(packet,true);
        String head = Udp.getHeadFromPacket(receiveMsg, Udp.Tag.p2p_send_file_info);
        if(head.equals(Udp.getHead(Udp.Tag.p2p_send_file_info,1))) {
//                            P2pClient.this.setSuspendHeartbeat(true); //停止心跳

            String lineBody = Udp.removeHeadFromPacket(receiveMsg,Udp.Tag.p2p_send_file_info);
            JSONObject jsonObject = JSONObject.parseObject(lineBody);
            fileName = jsonObject.getString("fileName");
            long size = jsonObject.getLong("size");
            packetSize = jsonObject.getInteger("packetSize");
            logger.info(String.format("准备接收文件[%s] 大小[%dB] 包数[%d]",fileName,size,packetSize)); //给出大约的大小和包数

            //告知对方已经收到文件信息
            byte[] receivedFileInfo= Udp.addHeadToPacket("", Udp.Tag.p2p_send_file_info, 2);
            client.sendPacket(clientSocket, receivedFileInfo, remotePublicIp, remotePublicPort);
            logger.info("告知对方已经收到文件信息");

            haveSavedFilePacketNumber.clear(); //已保持的数据包清空
        }
    }

    /**
     * 接收文件
     * @param data 数据
     */
    private void receiveFile(byte[] data) {
        int number = Udp.getNumberBytesFromPacket(data); //包序号
        if(haveSavedFilePacketNumber.contains(number)){ //重复发送的包，不能重复保存，可以重复通知对方
            logger.info("repeat file packet! number is " + number);

        }else{  //重复包不能一直追加保存！
            byte[] bytes = Udp.removeHeadBytesFromPacket(data,Udp.Tag.p2p_send_file);
            if(number==1){ //第一个包
                logger.info("开始接收文件...");
                try {
                    fos = new FileOutputStream(dir + (dir.endsWith(File.separator)?"":File.separator) + fileName);
                } catch (FileNotFoundException e) {
                    logger.info("保存文件时，找不到文件路径！");
                }
            }
            try {
                fos.write(bytes);
                fos.flush();
                haveSavedFilePacketNumber.add(number); //此包已保存

                if(number%100==0 || number == packetSize) {
                    logger.info(String.format("已经接收到文件,大小[%dKB] 包数[%d]", (int)((number - 1) * (long) (max_packet_size-7)/1000.0) + bytes.length, number));
                }
            } catch (IOException e) {
                logger.error("文件保存异常！", e);
            }
        }

        //通知已经收到一个包（重复通知可以的！）
        byte[] result = Udp.addHeadToPacket("",Udp.Tag.p2p_send_file,number);
        client.sendPacket(clientSocket,result,remotePublicIp,remotePublicPort);

        if(number == packetSize){ //最后一个包
            try {
                fos.close();
            } catch (IOException e) {
                logger.error("文件关闭异常！");
            }
            logger.info("文件接收完毕！");
//          P2pClient.this.setSuspendHeartbeat(true); //开启心跳
        }
    }

    /**
     * 发送消息、文件
     */
    private void sendMsg() {
        System.out.println("please input something to send:");
        Scanner scanner2 = new Scanner(System.in); //从标准输入流中读取数据
        String line = null;
        while (!(line = scanner2.nextLine()).equals("exit")) {
            if("sf".equals(line)){ //同步传输文件（因为传输的同时要等待确认消息，保持包顺序发送，只能允许一个线程接收消息）
                System.out.print("请输入要发送的文件名，退出输入exit：");
                String fileName = scanner2.nextLine();
//              this.setSuspendHeartbeat(true); //停心跳
                this.setSuspendReceiveMsg(true); //最多2s超时后会被挂起
                while(!haveSuspendReceiveMsg){
                    //等待挂起接收消息的进程后，方可传输文件
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        logger.error("等待挂起接收消息的线程时被打断！");
                    }
                }

                //传输文件
                this.sendFile(fileName);

//              this.setSuspendHeartbeat(false); //开启心跳
                this.setSuspendReceiveMsg(false);
            }else{
                System.out.println(); //换行
                byte[] formatLine= Udp.addHeadToPacket(line, Udp.Tag.p2p_send_msg, 1);
                client.sendPacket(clientSocket,formatLine, remotePublicIp, remotePublicPort); //正式发送消息
                System.out.println("you say:" + line);
                System.out.println("please input something to send:");
            }
        }
    }

    /**
     * 传输文件(发送方)
     * @param fileName 文件名（含路径）
     */
    private void sendFile(String fileName) {
        long startTime = System.currentTimeMillis();

        File file = new File(fileName);
        long size = file.length();
        int bufferSize = max_packet_size-7; //少了头部的字节数
        long packetSize = size % bufferSize == 0 ? size/bufferSize : size/bufferSize +1; //如果每次读取的满buffer应该是对的
        logger.info(String.format("准备传输文件[%s] 大小[%dB] 包数[%d]",fileName,size,packetSize));
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileName",fileName.substring(fileName.lastIndexOf(File.separator)+1));
        jsonObject.put("size",size);
        jsonObject.put("packetSize",packetSize);
        byte[] fileInfo = Udp.addHeadToPacket(jsonObject.toJSONString(), Udp.Tag.p2p_send_file_info, 1);
        client.sendPacket(clientSocket, fileInfo, remotePublicIp, remotePublicPort); //告诉对方文件大小
        //收到确认消息后发包
        while(true){
            DatagramPacket packet = client.getPacket(clientSocket, max_packet_size, socketTimeout);
            String receiveMsg = client.getPacketStr(packet,true);
            String head = Udp.getHeadFromPacket(receiveMsg, Udp.Tag.p2p_send_file_info);
            if(head.equals(Udp.getHead(Udp.Tag.p2p_send_file_info,2))) { //收到确认消息
                if(!beginToSendFile){ //没开始传输
                    beginToSendFile = true; //开始传输，实现幂等
                    logger.info(String.format("开始传输文件[%s]",fileName));
                    try {
                        FileInputStream fis = new FileInputStream(file);
                        byte[] buffer= new byte[bufferSize];
                        int readSize = 0;
                        int packetIndex = 1;
                        long haveSent = 0;
                        while((readSize = fis.read(buffer))!=-1){ //not the end of file
                            byte[] bytes = Udp.addHeadToPacket(buffer,readSize,Udp.Tag.p2p_send_file,packetIndex);
                            client.sendPacket(clientSocket,bytes,bytes.length,remotePublicIp,remotePublicPort); //发送数据包

                            haveSent += readSize;
//                            if(haveSent != (packetIndex-1)*(long)bufferSize + readSize){
//                                logger.info("------------说明文件读取不是每次满buffer的！-----------------");
//                            }
//                            System.out.println(String.format("已发送第%d个包，已发送大小%dB",packetIndex,haveSent));

                            //确认是否发送成功后，继续
                            int tryWaitTimes = 3; //尝试
                            int tryTimes = 30; //发10次包都丢失，放弃
                            while (true){
                                 packet = client.getPacket(clientSocket, max_packet_size, socketTimeout);
                                 receiveMsg = client.getPacketStr(packet,true);
                                 head = Udp.getHeadFromPacket(receiveMsg, Udp.Tag.p2p_send_file);
                                if(head.equals(Udp.getHead(Udp.Tag.p2p_send_file,packetIndex))){
                                    //确认发送成功此包
                                    if(packetIndex%100==0  || packetIndex == packetSize ){
                                        System.out.println(String.format("成功发送第%d个包，已发送大小%dKB",packetIndex,(int)(haveSent/1000.0)));
                                    }
                                    break;
                                }
                                if(--tryWaitTimes == 0){ //尝试三次了，还是没有收到确认消息，重新发包
                                    client.sendPacket(clientSocket,bytes,bytes.length,remotePublicIp,remotePublicPort); //发送数据包
                                    logger.info("等待3次消息，收不到文件包收到确认消息，重新发送数据包！");
                                    tryWaitTimes = 3; //重新等待
                                }
                                if(--tryTimes==0){
                                    logger.info("尝试发送数据包10次，也收不到文件包收到确认消息，放弃尝试！");
                                    break;
                                }
                            }
                            packetIndex++;
                        }
                        fis.close();
                    } catch (FileNotFoundException e) {
                        logger.error("找不到文件！",e);
                    }catch (IOException ioEx){
                        logger.error("文件操作异常",ioEx);
                    }
                    break; //退出循环
                }
            }
        }
        long endTime = System.currentTimeMillis();
        logger.info(String.format("文件[%s] 传输完毕！耗时%d秒",fileName,(int)((endTime-startTime)/1000.0)));
        beginToSendFile = false; //开始发送文件取消
    }

}
