package demo.msa.rpc.client;

import demo.msa.rpc.common.bean.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RPC客户端处理器（用于处理RPC响应）
 * @author wendy
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private static final Logger logger = LoggerFactory.getLogger(RpcClientHandler.class);

    /**
     *存放请求编号与响应对象之间的映射关系
     */
    private ConcurrentMap<String, RpcResponse> responseMap = new ConcurrentHashMap<>();

    public RpcClientHandler(ConcurrentMap<String, RpcResponse> responseMap) {
        this.responseMap = responseMap;
    }

    @Override
    public void channelRead0(ChannelHandlerContext channelHandlerContext, RpcResponse response) throws Exception {
        //建立请求编号与响应对象之间的映射关系
        responseMap.put(response.getRequestId(),response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause)throws Exception{
        logger.error("client caught exception",cause);
        ctx.close();
    }
}
