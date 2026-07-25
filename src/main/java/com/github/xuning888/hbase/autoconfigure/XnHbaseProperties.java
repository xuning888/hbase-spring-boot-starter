package com.github.xuning888.hbase.autoconfigure;


import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author xuning
 * @date 2026/7/25 17:04
 */
@ConfigurationProperties(prefix = "xn.hbase")
public class XnHbaseProperties {

    public boolean enabled;

    private String zookeeperQuorum;

    private Integer zookeeperPropertyClientPort;

    private Long rpcTimeout = 2000L;

    private Long clientRetriesNumber = 2L;

    private Auth auth = new Auth();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getZookeeperQuorum() {
        return zookeeperQuorum;
    }

    public void setZookeeperQuorum(String zookeeperQuorum) {
        this.zookeeperQuorum = zookeeperQuorum;
    }

    public Integer getZookeeperPropertyClientPort() {
        return zookeeperPropertyClientPort;
    }

    public void setZookeeperPropertyClientPort(Integer zookeeperPropertyClientPort) {
        this.zookeeperPropertyClientPort = zookeeperPropertyClientPort;
    }

    public Long getRpcTimeout() {
        return rpcTimeout;
    }

    public void setRpcTimeout(Long rpcTimeout) {
        this.rpcTimeout = rpcTimeout;
    }

    public Long getClientRetriesNumber() {
        return clientRetriesNumber;
    }

    public void setClientRetriesNumber(Long clientRetriesNumber) {
        this.clientRetriesNumber = clientRetriesNumber;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public class Auth {
        //默认开启安全认证
        private boolean enabled = true;

        private String user;

        private String domain;

        private String keytab;

        private String krb5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getKeytab() {
            return keytab;
        }

        public void setKeytab(String keytab) {
            this.keytab = keytab;
        }

        public String getKrb5() {
            return krb5;
        }

        public void setKrb5(String krb5) {
            this.krb5 = krb5;
        }
    }
}
