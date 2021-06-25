package com.hdwang.udp;

import java.net.DatagramSocket;

/**
 * Created by hdwang on 2017-03-27.
 */
public class UdpClient {

    public static void main(String[] args){
        //客户端socket
//        Udp client = new Udp(); //不绑定端口号，肯定收不到消息
        Udp client = new Udp(12001); //绑定端口号
        DatagramSocket clientSocket= client.init();

        int byteLen = 300; //子母站一个字节，汉子占3个字节（最多100个汉子）
        client.getMsgKeep(clientSocket, byteLen); //接收消息
        client.sendMsgKeep(clientSocket, "127.0.0.1", 12000); //发送消息
    }

}
