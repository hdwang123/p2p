package com.hdwang.utils;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by hdwang on 2017-03-31.
 */
public class IpUtil {

    /**
     * 获取本机ip地址
     * @return ip地址
     */
    public static String getLocalIp(){
        try {
            return Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException("获取本地IP地址失败！",e);
        }
    }

    /**
     * 获取本机所有ip地址
     * @return ip地址
     */
    public static String[] getAllLocalIp(){
        List<String> ipv4List = getAllLocalIpList();
        String[] ipv4s = new String[ipv4List.size()];
        for(int i=0;i<ipv4List.size();i++){
           ipv4s[i] =  ipv4List.get(i);
        }
        return ipv4s;
    }


    public static List<String> getAllLocalIpList(){
        List<String> ipv4List = new ArrayList<>();
        //get all local ips
        Enumeration<NetworkInterface> interfs = null;
        try {
            interfs = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new RuntimeException("获取本地IP地址失败！",e);
        }
        while (interfs.hasMoreElements())
        {
            NetworkInterface interf = interfs.nextElement();
            Enumeration<InetAddress> addres = interf.getInetAddresses();
            while (addres.hasMoreElements())
            {
                InetAddress in = addres.nextElement();
                if (in instanceof Inet4Address)
                {
                    ipv4List.add(in.getHostAddress());
                    // System.out.println("v4:" + in.getHostAddress());
                }
                else if (in instanceof Inet6Address)
                {
                    // System.out.println("v6:" + in.getHostAddress());
                }
            }
        }
        return ipv4List;
    }

}
