package demo.msa.rpc.client;

import demo.msa.rpc.common.bean.RpcRequest;
import demo.msa.rpc.common.bean.RpcResponse;
import demo.msa.rpc.common.codec.RpcDecoder;
import demo.msa.rpc.common.codec.RpcEncoder;
import demo.msa.rpc.registry.ServiceDiscovery;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RPC客户端(用于创建RPC服务代理)
 * @author wendy
 */
@Component
public class RpcClient {
    private static final Logger logger = LoggerFactory.getLogger(RpcClient.class);
    @Autowired
    private ServiceDiscovery serviceDiscovery;
    /**
     * 存放请求编号与响应对象之间的映射关系
     */
    private ConcurrentMap<String, RpcResponse> responseMap = new ConcurrentHashMap<>();

    public <T> T create(final Class<?> interfaceClass){
        //创建动态代理对象
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[]{interfaceClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                //创建RPC请求对象
                RpcRequest request = new RpcRequest();
                request.setRequestId(UUID.randomUUID().toString());
                request.setInterfaceName(method.getDeclaringClass().getName());
                request.setMethodName(method.getName());
                request.setParameterTypes(method.getParameterTypes());
                request.setParameters(args);
                //获取RPC服务地址
                String serviceName = interfaceClass.getName();
                String serviceAddress = serviceDiscovery.discover(serviceName);
                logger.debug("discover service:{} => {}",serviceName,serviceAddress);
                if(StringUtils.isEmpty(serviceAddress)){
                    throw new RuntimeException("server address is empty");
                }
                //从RPC服务地址中解析主机名和端口号
                String[] array = StringUtils.split(serviceAddress,":");
                String host = array[0];
                int port =Integer.parseInt(array[1]);
                //发送RPC请求
                RpcResponse response = send(request,host,port);
                if(response == null ){
                    logger.error("send request failure",new IllegalStateException("response is null"));
                    return  null;
                }
                if(response.hasException()){
                    logger.error("response has exception",response.getException());
                    return  null;
                }
                //获取响应结果
                return  response.getResult();
            }
        });
    }

    private RpcResponse send(RpcRequest request, String host, int port) {
        EventLoopGroup group = new NioEventLoopGroup(1);//单线程模式
        try {
            //创建RPC连接
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) throws Exception {
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new RpcEncoder(RpcRequest.class));
                    pipeline.addLast(new RpcDecoder(RpcResponse.class));
                    pipeline.addLast(new RpcClientHandler(responseMap));
                }
            });
            ChannelFuture future = bootstrap.connect(host,port).sync();
            //写入RPC请求对象
            Channel channel = future.channel();
            channel.writeAndFlush(request).sync();
            channel.closeFuture().sync();
            //获取RPC响应对象
            return responseMap.get(request.getRequestId());
        }catch (Exception e){
            logger.error("client exception",e);
            return null;
        }finally {
            group.shutdownGracefully();
            responseMap.remove(request.getRequestId());
        }
    }
}
