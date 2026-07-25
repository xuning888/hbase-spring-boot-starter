package com.github.xuning888.hbase.autoconfigure;


import com.github.xuning888.hbase.component.HbaseComponent;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.shaded.org.apache.commons.io.FileUtils;
import org.apache.hadoop.hbase.shaded.org.apache.commons.io.IOUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author xuning
 * @date 2026/7/25 17:07
 */
@Configuration
@EnableConfigurationProperties(XnHbaseProperties.class)
@ConditionalOnProperty(name = "xn.hbase.enabled", havingValue = "true")
public class XnHbaseAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(XnHbaseAutoConfiguration.class);

    private static final ScheduledThreadPoolExecutor scheduled = new ScheduledThreadPoolExecutor(1);


    @Bean
    public Connection hbaseConnection(XnHbaseProperties imHbaseProperties, ResourceLoader resourceLoader) throws IOException {

        //kerberos start
        if (imHbaseProperties.getAuth().isEnabled()) {
            String krb5 = imHbaseProperties.getAuth().getKrb5();

            if (krb5.startsWith("classpath")) {
                Resource krb5Resource = resourceLoader.getResource(imHbaseProperties.getAuth().getKrb5());
                String filePath = getFilePath(krb5Resource);
                logger.info("krb5 path={}", filePath);
                System.setProperty("java.security.krb5.conf", filePath);
            } else {
                System.setProperty("java.security.krb5.conf", krb5);
            }
        }
        //kerberos end

        //zookeeper start
        org.apache.hadoop.conf.Configuration config = HBaseConfiguration.create();
        config.set("hbase.zookeeper.quorum", imHbaseProperties.getZookeeperQuorum());
        config.setInt("hbase.zookeeper.property.clientPort", imHbaseProperties.getZookeeperPropertyClientPort());


        /**
         * 重试配置
         *
         * hbase.client.pause 重试间隔，使用默认值，100ms
         */
        //重试次数
        config.setLong("hbase.client.retries.number", imHbaseProperties.getClientRetriesNumber());

        /**
         * 超时配置
         *
         * 统一设置默认值，2000ms
         */
        //表示一次RPC请求的超时时间
        config.setLong("hbase.rpc.timeout", imHbaseProperties.getRpcTimeout());
        //一次操作总的超时（包含多个rpc请求，不包含scan操作）
        config.setLong("hbase.client.operation.timeout", imHbaseProperties.getRpcTimeout());
        //一次scan超时
        config.setLong("hbase.client.scanner.timeout.period", imHbaseProperties.getRpcTimeout());

        config.set("zookeeper.znode.parent", "/hbase");
        //zookeeper end

        if (imHbaseProperties.getAuth().isEnabled()) {
            //HBase security start
            config.set("hadoop.security.authentication", "kerberos");
            config.set("hbase.cluster.distributed", "true");
            config.set("hbase.security.authentication", "kerberos");
            String principal = String.format("hbase/_HOST@%s", imHbaseProperties.getAuth().getDomain());
            config.set("hbase.master.kerberos.principal", principal);
            config.set("hbase.regionserver.kerberos.principal", principal);
            UserGroupInformation.setConfiguration(config);
            String userName = String.format("%s@%s", imHbaseProperties.getAuth().getUser(), imHbaseProperties.getAuth().getDomain());

            //kerberos认证登陆
            String keytab = imHbaseProperties.getAuth().getKeytab();
            if (keytab.startsWith("classpath")) {
                Resource keytabResource = resourceLoader.getResource(imHbaseProperties.getAuth().getKeytab());
                String filePath = getFilePath(keytabResource);
                logger.info("keytab path={}", filePath);
                UserGroupInformation.loginUserFromKeytab(userName, filePath);
            } else {
                UserGroupInformation.loginUserFromKeytab(userName, keytab);
            }

            freshKerberos();
            //HBase security end
        }

        //获取连接
        Connection connection = ConnectionFactory.createConnection(config);
        logger.info("init hbase connect success");
        return connection;
    }

    /**
     * 获取文件路径
     *
     * @param resource
     * @return
     * @throws IOException
     */
    private String getFilePath(Resource resource) throws IOException {
        String path = resource.getURL().getPath();
        if (path.contains("BOOT-INF/classes!")) {
            // springboot jar启动
            return getFilePathFromJar(resource);
        } else {
            return path;
        }
    }


    /**
     * 从jar包中获取文件路径
     * 配置文件在jar里，没法获取文件的绝对路径。
     * 采用读取文件，然后生成一个磁盘临时文件的方式来获取path
     *
     * @param resource
     * @return
     * @throws IOException
     */
    private String getFilePathFromJar(Resource resource) throws IOException {
        String filename = resource.getFilename();
        ApplicationHome applicationHome = new ApplicationHome(getClass());
        //获取jar包路径，单元测试下获取不到
        String jarPath = applicationHome.getSource().getParentFile().toString();

        File file = new File(jarPath + File.separator + filename);
        InputStream inputStream = resource.getInputStream();
        try {
            FileUtils.copyInputStreamToFile(inputStream, file);
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
        logger.info("generate file path={}", file.getPath());
        return file.getPath();
    }

    @Bean
    public HbaseComponent hbaseComponent(@Qualifier("hbaseConnection") Connection hbaseConnection) {
        return new HbaseComponent(hbaseConnection);
    }

    /**
     * 定时执行刷新kerberos的认证
     */
    private void freshKerberos() {
        scheduled.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    UserGroupInformation.getLoginUser().checkTGTAndReloginFromKeytab();
                    logger.info("UserGroupInformation relogin.");
                } catch (IOException e) {
                    logger.error("refresh kerberos UserGroupInformation error: ", e);
                }
            }
        }, 1, 1, TimeUnit.HOURS);
    }
}
