package demo.msa.rpc.server;

import demo.msa.rpc.common.bean.RpcRequest;
import demo.msa.rpc.common.bean.RpcResponse;
import demo.msa.rpc.common.codec.RpcDecoder;
import demo.msa.rpc.common.codec.RpcEncoder;
import demo.msa.rpc.registry.ServiceRegistry;
import demo.msa.rpc.util.CollectionUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@Component
public class RpcServer implements ApplicationContextAware, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(RpcService.class);
    /**
     * 存放服务名称与服务实例之间的映射关系
     */
    private Map<String,Object> handlerMap = new HashMap<String,Object>();
    @Value("${rpc.port}")
    private int port;
    @Autowired
    private ServiceRegistry serviceRegistry;
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.info("coming in RpcServer");
        //扫描带有@RpcService注解的服务类
        Map<String,Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(RpcService.class);
        if(CollectionUtil.isNotEmpty(serviceBeanMap)){
            for(Object serviceBean : serviceBeanMap.values()){
                RpcService rpcService = serviceBean.getClass().getAnnotation(RpcService.class);
                String serviceName = rpcService.value().getName();
                handlerMap.put(serviceName,serviceBean);
            }
        }

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        EventLoopGroup childGroup = new NioEventLoopGroup();
        try {
            //启动RPC服务
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group,childGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>(){
                @Override
                public void initChannel(SocketChannel socketChannel) throws Exception {
                    ChannelPipeline pipeline = socketChannel.pipeline();
                    pipeline.addLast(new RpcDecoder(RpcRequest.class));//解码RPC请求
                    pipeline.addLast(new RpcEncoder(RpcResponse.class));//编码RPC请求
                    pipeline.addLast(new RpcServerHandler(handlerMap));//处理RPC请求
                }
            });
            ChannelFuture future = bootstrap.bind(port).sync();
            log.debug("server started listening on {}",port);
            //注册RPC服务地址
            String serviceAddress = InetAddress.getLocalHost().getHostAddress()+":"+port;
            for(String interfaceName: handlerMap.keySet()){
                serviceRegistry.register(interfaceName,serviceAddress);
                log.debug("register service {} => {}",interfaceName,serviceAddress);
                //释放资源
                future.channel().closeFuture().sync();
            }
        }catch (Exception e){
            log.error("server exception", e);
        }finally {
            //关闭RPC服务
            childGroup.shutdownGracefully();
            group.shutdownGracefully();
        }

    }
}
