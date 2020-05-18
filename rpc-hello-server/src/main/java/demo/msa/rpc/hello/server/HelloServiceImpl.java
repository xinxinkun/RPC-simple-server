package demo.msa.rpc.hello.server;

import demo.msa.rpc.hello.api.HelloService;
import demo.msa.rpc.server.RpcService;

/**
 * RPC接口实现
 * @author wendy
 */
@RpcService(HelloService.class)
public class HelloServiceImpl implements HelloService {
    @Override
    public String say(String name) {
        return "hello "+ name;
    }
}
