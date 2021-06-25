package com.hdwang.model;

/**
 * Created by hdwang on 2017-03-29.
 */
public class ClientPc {

    /**
     * 客户端标识
     */
    private String id;

    /**
     * 客户端公网ip
     */
    private String publicIp;

    /**
     * 客户端公网port
     */
    private int publicPort;

    /**
     * 是否支持p2p
     */
    private boolean isSupportP2p;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPublicIp() {
        return publicIp;
    }

    public void setPublicIp(String publicIp) {
        this.publicIp = publicIp;
    }

    public int getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(int publicPort) {
        this.publicPort = publicPort;
    }

    public boolean isSupportP2p() {
        return isSupportP2p;
    }

    public void setSupportP2p(boolean isSupportP2p) {
        this.isSupportP2p = isSupportP2p;
    }
}
