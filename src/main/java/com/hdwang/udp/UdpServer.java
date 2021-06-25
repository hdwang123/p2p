package com.hdwang.udp;

import java.net.DatagramSocket;

/**
 * Created by hdwang on 2017-03-27.
 */
public class UdpServer {

    public static void main(String[] args){
        //服务端socket
        Udp server = new Udp(12000); //绑定端口号
        DatagramSocket serverSocket= server.init();

        int byteLen = 300;
        server.getMsgKeep(serverSocket,byteLen); //接收消息
        server.sendMsgKeep(serverSocket, "127.0.0.1", 12001); //发送消息

        //端口复用尝试失败
//        Udp server2 = new Udp(12000);
//        DatagramSocket serverSocket2 = server.init();
//        server.getMsg(serverSocket2,byteLen); //接收消息
//        server.sendMsg(serverSocket2, "127.0.0.1", 12001); //发送消息
    }

}
