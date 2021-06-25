package com.hdwang;

import com.hdwang.p2p.P2pClient;
import com.hdwang.p2p.P2pServer;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.swing.plaf.synth.SynthTextAreaUI;
import java.util.Arrays;
import java.util.Iterator;


/**
 * Created by hdwang on 2017-03-26.
 *
 * 注意：关闭防火墙
 *
 * 1.检测主机是否支持p2p执行1、2两步即可，检测具体nat类型，需要走完所有步骤
 *   1.1 客户端C1发请求给服务器S1->S1回复公网地址+端口号->客户端C1检测ip与公网ip是否一致,一致主机C1为公网机器，否则为NAT
 *   1.2 C1再发请求给服务器S1另一个端口号(或者另一台服务器S2)->服务器返回公网ip端口号->客户端检测的两次请求的公网端口号是否一致，一致则支持p2p（cone nat）,否则不支持p2p(symmetrical nat)
 *   1.3 S1将C1的公网地址告诉另一台服务器S2->S2直接发请求->C1可以收到 full cone(目标ip+port均不限制)
 *   1.4 1.3收不到，但S1的另一个端口号发请求给C1->C1可以收到 restricted cone(限制ip限制，不限制port),打洞(C1首先发包给S1)后可解除限制
 *   1.5 1.4收不到,就是port restricted cone,,打洞(C1首先发包给S1指定端口发包)后可解除限制
 *
 *2.服务器S1给客户端C1和C2建立p2p链接
 *  2.1 C1->S1，S1保存C1 public ip+port
 *  2.2 C2->S1, S1保存C2 public ip+port
 *  2.3 S1->C1,C2, S1告知对方的 public ip+port
 *  2.4 C1<-> C2 ， C1和C2相互发包，直到建立连接（均能收到对方包为止）
 *
 * 程序结构
 *  1. C1请求S1，帮自己和C2建立p2p链接
 *  2. S1询问C2同意否？
 *  3. C2同意后，S1检查C1、C2是否支持p2p
 *  4. 均支持p2p后，告知C2与C1建立p2p连接
 *  5. C1，C2互相发包给对方，打通p2p通道
 *
 *  功能点
 *  1.检测p2p
 *  2.建立p2p连接以传输文件
 *
 */
public class Application {

    /**
     * 日志
     */
    private final static Logger logger = LoggerFactory.getLogger(Application.class);

    /**
     * main class
     * @param args params for application
     */
    public static void main(String[] args) {

        logger.debug("debug test!");
        logger.info("args is:" + Arrays.toString(args));

        Configuration configuration = getConfig();
        logger.info("load config file success,config info is:\r\n\r\n" + getConfigInfo(configuration)+"\r\n");

        try {
            String args0 = args[0];
            if("-s".equals(args0)){
                logger.info("begin to start p2pserver");
                P2pServer server = new P2pServer(configuration);
                server.run();
            }else if("-c".equals(args0)){
                logger.info("begin to start p2pclient");
                P2pClient client = new P2pClient(configuration);
                client.run();
            }
        }catch (Exception ex){
            logger.error(ex.getMessage(),ex);
        }
    }

    /**
     * 加载配置文件
     * @return 配置文件对象
     */
    private static Configuration getConfig(){
        Configuration config = null;
        try {
            //运行时classpath路径：F:/study/projects/p2p/target/classes/config.properties(默认配置文件路径，输出到classes文件夹下，最终会打进项目包中)
            //打包后classpath路径：项目运行目录config/config.properties（配置文件外置后路径）
            config = new PropertiesConfiguration(Application.class.getClassLoader().getResource("config.properties"));
        } catch (ConfigurationException e) {
            throw new RuntimeException("load config file failed",e);
        }
        return config;
    }

    /**
     * 打印所有的配置信息
     * @param configuration 配置对象
     * @return 配置信息
     */
    private static String getConfigInfo(Configuration configuration){
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        Iterator<String> iterator = configuration.getKeys();
        while(iterator.hasNext()){
            String key = iterator.next();
            sb.append(key+":"+ configuration.getProperty(key)+",");
        }
        String info = sb.toString();
        return info.substring(0,info.length()-1)+"}";
    }
}
