package com.hdwang.p2p;

import com.alibaba.fastjson.JSONObject;
import com.hdwang.model.ClientPc;
import com.hdwang.udp.Udp;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by hdwang on 2017-03-26.
 * p2p服务端
 */
public class P2pServer {
    /**
     * 日志
     */
    private final static Logger logger = LoggerFactory.getLogger(P2pServer.class);

    /**
     * 服务端端口号
     */
    private int port;

    /**
     * 缓冲器大小
     */
    private int max_packet_size;

    private int socketTimeout = 2000;

    /**
     * 连接上服务器的所有client
     */
    private List<ClientPc> clientPcs = new ArrayList<>();

    /**
     * 构造函数
     * @param configuration 配置对象
     */
    public P2pServer(Configuration configuration){
        port = configuration.getInt("server.port");
        max_packet_size = configuration.getInt("packet.size");
        int anotherPort = 18000; //configuration.getInt("server.anotherPort");
    }

    /**
     * 开始跑
     */
    public void run() {

        p2pConnectHelp();

    }

    /**
     * 服务端协助p2p连接
     */
    private void p2pConnectHelp(){
        //线程1，监听端口号：port
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //服务端开启一个地址的监听
                Udp server = new Udp(port); //绑定端口号
                DatagramSocket serverSocket= server.init();
                while(true){
                    DatagramPacket packet = server.getPacket(serverSocket, max_packet_size,socketTimeout); //接收包
                    if(packet==null || packet.getAddress()== null){
                        continue;
                    }
                    String remoteIp = packet.getAddress().getHostAddress(); //也是远程地址
                    int remotePort = packet.getPort(); //也是远程port
                    logger.info("get client public network address:"+remoteIp+":"+remotePort);

                    String line = server.getPacketStr(packet,true);
                    String tag = Udp.getTagFromPacket(line);
                    if(Udp.Tag.p2p_support_check.equals(tag)){  //p2p检测请求
                        String head = Udp.getHeadFromPacket(line,Udp.Tag.p2p_support_check);
                        if(head.equals(Udp.getHead(Udp.Tag.p2p_support_check,1))){  //p2p检测，答复公网ip+port即可
                            String sendMsg = remoteIp+":"+remotePort;
                            try {
                                server.sendPacket(serverSocket,sendMsg.getBytes("UTF-8"),remoteIp,remotePort);
                            } catch (UnsupportedEncodingException e) {
                                logger.error("不支持的编码格式！");
                            }
                        }else if(head.equals(Udp.getHead(Udp.Tag.p2p_support_check,2))){  //p2p检测，获取客户端是否支持p2p信息
                            String lineBody = Udp.removeHeadFromPacket(line,Udp.Tag.p2p_support_check);
                            JSONObject jsonObject= JSONObject.parseObject(lineBody);
                            String clientId = jsonObject.getString("id");
                            ClientPc clientPc =  findClientPcById(clientId);  //先找找看，是否存在同样的客户机
                            if(clientPc==null){
                                clientPc =  new ClientPc();
                                clientPcs.add(clientPc);  //保存在线的客户端
                            }
                            clientPc.setId(clientId);
                            clientPc.setSupportP2p("y".equals(jsonObject.getString("isSupport")) ? true : false);
                            clientPc.setPublicIp(remoteIp);
                            clientPc.setPublicPort(remotePort);
                        }

                    }else if(Udp.Tag.p2p_connect_try.equals(tag)){ //p2p协助建立连接请求
                        //TODO 目前强行建立连接尝试，无需对方同意
                        String head = Udp.getHeadFromPacket(line,Udp.Tag.p2p_connect_try);
                        if(head.equals(Udp.getHead(Udp.Tag.p2p_connect_try,1))){  //告知双方对方的公网信息
                            String lineBody = Udp.removeHeadFromPacket(line,Udp.Tag.p2p_connect_try);
                            JSONObject jsonObject= JSONObject.parseObject(lineBody);
                            String clientId = jsonObject.getString("id");
                            String remoteId = jsonObject.getString("remoteId");
                            ClientPc clientPc1 = findClientPcById(clientId);
                            ClientPc clientPc2 = findClientPcById(remoteId);
                            //p2p连接是否支持检测
                            boolean support = true;
                            if(!clientPc1.isSupportP2p() || !clientPc2.isSupportP2p()){
                                support = false;
                                logger.info(String.format("client %s not support p2p to client %s",clientPc1.getId(),clientPc2.getId()));
                            }
                            //回复消息给client
                            jsonObject = new JSONObject();
                            jsonObject.put("id",clientId);
                            jsonObject.put("remoteId",remoteId);
                            jsonObject.put("support",support?"y":"n");
                            jsonObject.put("remotePublicIp",clientPc2.getPublicIp());
                            jsonObject.put("remotePublicPort",clientPc2.getPublicPort());
                            try {
                                server.sendPacket(serverSocket,jsonObject.toJSONString().getBytes("UTF-8"),remoteIp,remotePort);
                            } catch (UnsupportedEncodingException e) {
                                logger.error("不支持的编码格式！");
                            }
                            logger.info("请求"+clientId+"建立p2p连接");

                            //回复消息给remoteClient
                            jsonObject = new JSONObject();
                            jsonObject.put("id",remoteId);
                            jsonObject.put("remoteId",clientId);
                            jsonObject.put("support",support?"y":"n");
                            jsonObject.put("remotePublicIp",clientPc1.getPublicIp());
                            jsonObject.put("remotePublicPort",clientPc1.getPublicPort());
                            try {
                                server.sendPacket(serverSocket,jsonObject.toJSONString().getBytes("UTF-8"),clientPc2.getPublicIp(),clientPc2.getPublicPort());
                            } catch (UnsupportedEncodingException e) {
                                logger.error("不支持的编码格式！");
                            }
                            logger.info("请求"+remoteId+"建立p2p连接");

                        }

                    }
                }
            }
        });
        thread.start();

        //线程2，监听端口号:port+1
        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                //服务端开启另一个端口的监听
                Udp server = new Udp(port+1); //绑定端口号
                DatagramSocket serverSocket= server.init();
                while(true){
                    DatagramPacket packet = server.getPacket(serverSocket, max_packet_size,socketTimeout); //接收包
                    if(packet==null || packet.getAddress()== null){
                        continue;
                    }
                    String remoteIp = packet.getAddress().getHostAddress(); //也是远程地址
                    int remotePort = packet.getPort(); //也是远程port
                    logger.info("get client public network address:"+remoteIp+":"+remotePort);

                    String line = server.getPacketStr(packet,true);
                    String tag = Udp.getTagFromPacket(line);
                    if(Udp.Tag.p2p_support_check.equals(tag)){ //p2p检测，答复公网ip+port即可

                        String sendMsg = remoteIp+":"+remotePort;
                        try {
                            server.sendPacket(serverSocket,sendMsg.getBytes("UTF-8"),remoteIp,remotePort);
                        } catch (UnsupportedEncodingException e) {
                            logger.error("不支持的编码格式！");
                        }
                    }
                }
            }
        });
        thread2.start();
    }


    /**
     * 找到匹配的在线客户端
     * @param id 客户端id
     * @return 客户端，找不到返回null
     */
    private ClientPc findClientPcById(String id){
        for(ClientPc clientPc:clientPcs){
            if (id.equals(clientPc.getId())){
                return clientPc;
            }
        }
        return null;
    }
}
