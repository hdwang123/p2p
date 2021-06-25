package com.hdwang.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

/**
 * Created by hdwang on 2017-03-26.
 *  tcp客户端
 */
public class Client {
    /**
     * 日志
     */
    private final static Logger logger = LoggerFactory.getLogger(Client.class);

    private String ip;
    private int port;
    private String clientIp;
    private int clientPort;

    public Client(String ip,int port){
        this.ip=ip;
        this.port = port;
    }

    public Client(String ip,int port,String clientIp,int clientPort){
        this.ip=ip;
        this.port = port;
    }

    /**
     * 启动客户端
     * @param addressReuse 地址是否复用
     * @return 客户端socket
     */
    public Socket init(boolean addressReuse){
        //启动服务端
        Socket client = null;
        try {
            client = new Socket();
            if(addressReuse){ //有固定的端口号
                client.setReuseAddress(true); //设置地址可重用，方可实现NAT打洞
                client.bind(new InetSocketAddress(clientPort));
                logger.info("bind client success");
            }
        } catch (IOException e) {
            throw new RuntimeException("init client failed",e);
        }
        logger.info("init client success");
        return client;
    }

    /**
     * 连接服务器
     * @param socket 客户端socket
     */
    public void connect(Socket socket){
        try {
            socket.connect(new InetSocketAddress(ip,port));
        } catch (IOException e) {
            throw new RuntimeException("connect server failed",e);
        }
        logger.info("connect server success");
    }

    private boolean exit=false;

    /**
     * 获取服务端请求信息
     * @param socket 套接字
     */
    public void getServerMsg(final Socket socket){

        //获取输入流，接收服务端的数据
        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Reader is = new InputStreamReader(socket.getInputStream(), "UTF-8");
                    BufferedReader br = new BufferedReader(is);
                    String line;
                    while ((line = br.readLine()) != null) { //从流中读取一行，读到就输出,未读到阻塞
                        System.out.println("client:" + line);
                        if(line.equals("exit")){
                            exit = true;
                        }
                    }
                } catch (IOException e) {
                    if(e instanceof SocketException){
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
     * 向服务端发送消息
     * @param socket 套接字
     */
    public void sendMsgToServer(final Socket socket){

        //获取输出流，向服务端写数据
        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Writer out = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
                    PrintWriter pw = new PrintWriter(out);

                    //输入字符串，传递给服务端
                    Scanner scaner = new Scanner(System.in); //从标准输入流中读取数据
                    String line = null;
                    //while (!(line = scaner.nextLine()).equals("exit")) {
                    while(true){
                        line = "make hole...";
                        pw.println(line); //句子带换行符，作为流中一个句子的分割标识
                        pw.flush();
                        if(exit){
                            logger.info("end make hole!");
                            break;
                        }
                        try {
                            Thread.sleep(30000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    if(e instanceof SocketException){
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
     * 关闭客户端连接
     * @param socket
     */
    public void close(Socket socket){
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException("close clientSocket failed",e);
        }
        logger.info("close clientSocket success");
    }
}
