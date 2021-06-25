package com.hdwang.udp;

import com.hdwang.utils.BytesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

/**
 * Created by hdwang on 2017-03-26.
 *
 *  udp没有客户端服务器的概念（一个socket即是服务端也是客户端）
 *
 *  udp自定义类型头格式为： Udp.Tag+数字
 */
public class Udp {

    /**
     * 日志
     */
    private final static Logger logger = LoggerFactory.getLogger(Udp.class);

    private int port;

    public Udp(){}

    public Udp(int port){
        this.port = port;
    }


    /**
     * 启动socket
     * @return udp socket
     */
    public DatagramSocket init(){
        //启动服务端
        DatagramSocket socket = null;
        try {
            if(this.port == 0){
                socket = new DatagramSocket(); //没有特定监听的端口，仅仅使用一个临时的，程序会让操作系统分配一个可用的端口
            }else{
                socket = new DatagramSocket(port); //绑定指定端口，监听端口的报文
//                socket.setReuseAddress(true);
//                socket.bind(new InetSocketAddress(InetAddress.getLocalHost(),port));
            }
        } catch (IOException e) {
            throw new RuntimeException("init DatagramSocket failed",e);
        }
        logger.info("init DatagramSocket success");
        return socket;
    }

    /**
     * 关闭服务器连接
     * @param socket
     */
    public void close(DatagramSocket socket){
        socket.close();
        logger.info("close DatagramSocket success");
    }

    /**
     * 接收包
     * @param socket 套接字
     * @param byteLen 包大小（字节数）
     * @param timeout 超时时间
     * @return 包
     */
    public DatagramPacket getPacket(DatagramSocket socket,int byteLen,int timeout){
        byte[] recvBuf = new byte[byteLen];
        DatagramPacket recvPacket = new DatagramPacket(recvBuf , recvBuf.length);
        try {
            socket.setSoTimeout(timeout); //超时时间
            socket.receive(recvPacket);
        }catch (SocketTimeoutException socketTimeoutEx){
//            logger.info("socket time out!");
        }
        catch (IOException e) {
            logger.error("get pack failed!",e);
        }
        return recvPacket;
    }

    /**
     * 获取包字符串
     * @param packet 包
     * @param haveHead 是否含头
     * @return 字符串
     */
    public String getPacketStr(DatagramPacket packet,boolean haveHead){
        String line = null;
        try {
            if(haveHead){
                byte[] data = packet.getData();
                byte[] head = new byte[7];
                System.arraycopy(data,0,head,0,7);
                byte[] body = new byte[packet.getLength()-7];
                System.arraycopy(data,7,body,0,packet.getLength()-7);
                line = new String(head,"UTF-8") + new String(body,"UTF-8"); //头部和身体分开计算方能正确
            }else{
                line = new String(packet.getData(),0,packet.getLength(),"UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("字节转中文字符串失败,不支持的编码格式!");
        }
//        logger.debug(String.format("get packet from %s:%s, content is:%s", packet.getAddress().getHostAddress(), packet.getPort(), line));
        return line;
    }

    /**
     * 发送包
     * @param socket 套接字
     * @param bytes 字节数组
     * @param ip 远程ID
     * @param port 远程端口号
     */
    public void sendPacket(DatagramSocket socket,byte[] bytes,String ip,int port){
       this.sendPacket(socket,bytes,bytes.length,ip,port);
    }

    /**
     * 发送包
     * @param socket 套接字
     * @param bytes 字节数组
     * @param length 发送字节数
     * @param ip 远程ID
     * @param port 远程端口号
     */
    public void sendPacket(DatagramSocket socket,byte[] bytes,int length,String ip,int port){
        try {
            DatagramPacket sendPacket = new DatagramPacket(bytes , length, InetAddress.getByName(ip) , port);
            socket.send(sendPacket);
        }catch (IOException e) {
            logger.error("send pack failed!",e);
        }
    }

    /**
     * 持续获取客户端请求信息
     * @param socket 客户端套接字
     */
    public void getMsgKeep(final DatagramSocket socket,final int byteLen){

        //获取输入流，接收客户端的数据
        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try {
                        byte[] recvBuf = new byte[byteLen];
                        DatagramPacket recvPacket = new DatagramPacket(recvBuf , recvBuf.length);
//                        socket.setSoTimeout(30000); //设置接收等待的超时时间
                        socket.receive(recvPacket);
                        String line = new String(recvPacket.getData() , 0 , recvPacket.getLength(),"UTF-8");
                        System.out.println(String.format("get packet from %s:%s, content is:%s",recvPacket.getAddress().getHostAddress(),recvPacket.getPort(),line));
                    } catch (IOException e) {
                        logger.error("receive failed!",e);
                        socket.close();
                        logger.info("socket closed.");
                    }
                }
            }
        });
        thread1.start(); //线程启动
    }

    /**
     * 持续向客户端发送消息
     * @param socket 客户端套接字
     */
    public void sendMsgKeep(final DatagramSocket socket,final String clientIp,final int clientPort){

        //获取输出流，向客户端写数据
        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //输入字符串，传递给客户端
                    Scanner scaner = new Scanner(System.in); //从标准输入流中读取数据
                    String line = null;
                    while (!(line = scaner.nextLine()).equals("exit")) {
                        byte[] sendBuf = line.getBytes("UTF-8");
                        DatagramPacket sendPacket = new DatagramPacket(sendBuf , sendBuf.length , InetAddress.getByName(clientIp) , clientPort);
                        socket.send(sendPacket);
                    }
                } catch (IOException e) {
                    logger.error("send failed!",e);
                    socket.close();
                    logger.info("socket closed.");
                }
            }
        });
        thread2.start(); //线程启动
    }

    /**
     * 给包添加头
     * @param data 数据
     * @param tag 标记
     * @param number 序号
     * @return 添加头后的数据
     */
    public static byte[] addHeadToPacket(String data,String tag,int number){
        byte[] head = getHeadBytes(tag, number); //7个字节
//        new String(head,"UTF-8"); //长度不一定是9
        byte[] dataBytes;
        try {
            dataBytes = data.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            dataBytes = data.getBytes();
        }
        byte[] bytes = new byte[dataBytes.length+7];
        System.arraycopy(head,0,bytes,0,head.length);
        System.arraycopy(dataBytes,0,bytes,head.length,dataBytes.length);
        return bytes;
    }

    /**
     * 给包添加头
     * @param data 数据
     * @param dataLength 数据大小
     * @param tag 标记
     * @param number 序号
     * @return 添加头后的数据
     */
    public static byte[] addHeadToPacket(byte[] data,int dataLength,String tag,int number){
        byte[] headBytes = getHeadBytes(tag, number);
        int size = dataLength + headBytes.length; //总字节数
        byte[] bytes = new byte[size];
        System.arraycopy(headBytes, 0, bytes, 0, headBytes.length); //添加头WW
        System.arraycopy(data,0,bytes,headBytes.length,dataLength); //添加数据
        return bytes;
    }


    /**
     * 去掉包的头
     * @param data 数据
     * @param tag 标记
     * @return 去掉头的包
     */
    public static String removeHeadFromPacket(String data,String tag){
        return data.substring(tag.length()+4);
    }

    public static byte[] removeHeadBytesFromPacket(byte[] data,String tag){
        byte[] bytes = new byte[data.length-7];
        System.arraycopy(data,7,bytes,0,data.length-7);
        return bytes;
    }

    /**
     * 获取包的头
     * @param data 数据
     * @param tag 标记
     * @return 头
     */
    public static String getHeadFromPacket(String data,String tag){
        return data.substring(0,tag.length()+4);
    }

    /**
     * 获取标记
     * @param data 数据
     * @return 标记
     */
    public static String getTagFromPacket(String data){
        return data.substring(0,3);
    }

    public static String getTagBytesFromPacket(byte[] data){
        try {
            return new String(data,0,3,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 获取包序号
     * @param data 数据
     * @return 包序号
     */
    public static int getNumberFromPacket(String data){
        byte[] numberBytes;
        try {
           numberBytes =  data.substring(3,7).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.info("不支持的编码格式",e);
            numberBytes =  data.substring(3,7).getBytes();
        }
        return BytesUtil.byte2Int(numberBytes);
    }

    public static int getNumberBytesFromPacket(byte[] data){
        byte[] numberBytes = new byte[4];
        System.arraycopy(data,3,numberBytes,0,4);
        return BytesUtil.byte2Int(numberBytes);
    }

    /**
     * 构建udp包的标志头
     * @param tag 标记
     * @param number 序号
     * @return 标志头
     */
    public static String getHead(String tag,int number){
        byte[] bytes = getHeadBytes(tag,number);
        try {
            return new String(bytes,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 构建udp包的标志头(7个字节,如果整体转出字符串utf-8在转回来就不一定是7了)
     * @param tag 标记
     * @param number 序号
     * @return 标志头
     */
    public static byte[] getHeadBytes(String tag,int number){
        try {
            byte[] tagBytes = tag.getBytes("UTF-8");
            byte[] numberBytes = BytesUtil.int2Byte(number);
            byte[] bytes = new byte[tagBytes.length+numberBytes.length];
            System.arraycopy(tagBytes,0,bytes,0,tagBytes.length);
            System.arraycopy(numberBytes,0,bytes,tagBytes.length,numberBytes.length);
            return bytes;
        } catch (UnsupportedEncodingException e) {
            logger.info("不支持的编码格式。",e);
        }
        return null;
    }

    /**
     * udp包头标记类型
     */
    public static interface Tag{
        /**
         * 包类型：p2p是否支持检测
         */
        String p2p_support_check = "001";

        /**
         * 包类型：p2p连接尝试
         */
        String p2p_connect_try = "002";

        /**
         * 包类型：心跳
         */
        String p2p_connect_heartbeat = "003";

        /**
         * 传输文本信息
         */
        String p2p_send_msg = "004";

        /**
         * 传输文件
         */
        String p2p_send_file = "005";

        /**
         * 传输文件包数量
         */
        String p2p_send_file_info ="006";
    }
}
