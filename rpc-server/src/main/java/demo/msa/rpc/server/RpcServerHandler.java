package demo.msa.rpc.server;

import demo.msa.rpc.common.bean.RpcRequest;
import demo.msa.rpc.common.bean.RpcResponse;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * RPC服务端处理器（用于处理RPC请求）
 * @author wendy
 */
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private static final Logger log = LoggerFactory.getLogger(RpcServerHandler.class);
    private final Map<String,Object> handlerMap;

    public RpcServerHandler(Map<String, Object> handlerMap) {
        this.handlerMap = handlerMap;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcRequest rpcRequest) throws Exception {
        RpcResponse response = new RpcResponse();
        response.setRequestId(rpcRequest.getRequestId());
        try {
            Object result = handle(rpcRequest);
            response.setResult(result);
        }catch (Exception e){
            response.setException(e);
            log.error("handle result fail",e);
        }
        //写入RPC响应请求
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private Object handle( RpcRequest request) throws  Exception {

        String serviceName = request.getInterfaceName();
        Object serviceBean = handlerMap.get(serviceName);
        if(serviceBean == null ){
            log.error("can not find service bean by key %S",serviceName);
            throw new RuntimeException();
        }
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();
        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        //执行反射调用
        Method method = serviceClass.getMethod(methodName,parameterTypes);
        method.setAccessible(true);
        Object result = method.invoke(serviceBean, parameters);
        return result;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("server caught exception", cause);
        ctx.close();
    }

}
