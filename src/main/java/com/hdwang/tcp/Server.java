package com.hdwang.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Created by hdwang on 2017-03-26.
 * tcp服务端
 */
public class Server {

    /**
     * 日志
     */
    private final static Logger logger = LoggerFactory.getLogger(Server.class);

    private int port;

    public Server(int port){
        this.port = port;
    }

    /**
     * 启动服务端
     * @param addressReuse 地址是否复用
     * @return 服务端socket
     */
    public ServerSocket init(boolean addressReuse){
        //启动服务端
        ServerSocket server = null;
        int port = this.port;
        try {
            server = new ServerSocket();
            if(addressReuse) {
                server.setReuseAddress(addressReuse); //设置地址可重用，方可实现NAT打洞
                server.bind(new InetSocketAddress(port));
            }else {
                server.bind(new InetSocketAddress(port)); //绑定地址
            }
            logger.info("bind server success");
        } catch (IOException e) {
            throw new RuntimeException("init server failed",e);
        }
        logger.info("init server success");
        return server;
    }

    /**
     * 接受连接
     * @param server 服务端套接字
     * @return 客户端套接字
     */
    public Socket accept(ServerSocket server){
            //接收客户端请求
            Socket client = null;
            try {
                client = server.accept();
            } catch (IOException e) {
                throw new RuntimeException("server accept failed",e);
            }
            logger.info("server get a client connection");
            return client;
        }

    /**
     * 获取客户端请求信息
     * @param socket 客户端套接字
     */
    public void getClientMsg(final Socket socket){

        //获取输入流，接收客户端的数据
        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Reader is = new InputStreamReader(socket.getInputStream(), "UTF-8");
                    BufferedReader br = new BufferedReader(is);
                    String line;
                    while ((line = br.readLine()) != null) { //从流中读取一行，读到就输出,未读到阻塞
                        System.out.println("client:" + line);
                    }
                } catch (IOException e) {
                    if(e instanceof  SocketException){
                        logger.error("connection disconnected!",e);
                    }
                    try {
                        socket.close();
                        logger.info("client socket closed.");
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        thread1.start(); //线程启动
    }

    /**
     * 向客户端发送消息
     * @param socket 客户端套接字
     */
    public void sendMsgToClient(final Socket socket){

        //获取输出流，向客户端写数据
        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Writer out = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
                    PrintWriter pw = new PrintWriter(out);

                    //输入字符串，传递给客户端
                    Scanner scaner = new Scanner(System.in); //从标准输入流中读取数据
                    String line = null;
                    while (!(line = scaner.nextLine()).equals("exit")) {
                        pw.println(line); //句子带换行符，作为流中一个句子的分割标识
                        pw.flush();
                    }
                } catch (IOException e) {
                    if(e instanceof  SocketException){
                        logger.error("connection disconnected!",e);
                    }
                    try {
                        socket.close();
                        logger.info("client socket closed.");
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        thread2.start(); //线程启动
    }


    /**
     * 关闭服务器连接
     * @param socket
     */
    public void close(ServerSocket socket){
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException("close serverSocket failed",e);
        }
        logger.info("close serverSocket success");
    }
}
