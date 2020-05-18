package demo.msa.rpc.registry;

import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;


/**
 * 服务注册
 * @author wendy
 */
@Component
public class ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);
    @Value("${rpc.registry-address}")
    private String zkAddress;
    private ZkClient zkClient;
    @PostConstruct
    public void init(){
        zkClient = new ZkClient(zkAddress, Constant.ZK_SESSION_TIMEOUT,Constant.ZK_CONNECTION_TIMEOUT);
        logger.info("connection to zookeeper");
    }
    public void register(String serviceName,String serviceAddress){
        //创建registry节点(持久)
        String registryPath = Constant.ZK_REGISTRY_PATH;
        if(!zkClient.exists(registryPath)){
            zkClient.createPersistent(registryPath);
            logger.info("create registry node: {}",registryPath);
        }
        //创建服务节点（持久）
        String servicePath = registryPath + "/"+serviceName;
        if(!zkClient.exists(servicePath)){
            zkClient.createPersistent(servicePath);
            logger.info("create service node: {}",servicePath);
        }
        //创建服务地址节点(临时)
        String addressPath = servicePath+"/address-";
        String addressNode = zkClient.createEphemeralSequential(addressPath,serviceAddress);
        logger.info("create addressNode:{}",addressNode);
    }
}
