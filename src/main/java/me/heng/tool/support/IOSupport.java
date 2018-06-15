package me.heng.tool.support;

import com.google.common.collect.Lists;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Function;

/**
 * Created by chuanbao on 3/17/17.
 *
 * @author abao
 * @date 2017/03/17
 */
public class IOSupport {

    public static <I, O> List<O> batchFetch(Collection<I> inputs, int size,
        Function<Collection<I>, Collection<? extends O>> fetcher) {
        if (BaseSupport.isEmpty(inputs)) {
            return Collections.emptyList();
        }
        List<O> results = new ArrayList<>(inputs.size());
        if (inputs.size() < size) {
            Collection<? extends O> l = fetcher.apply(inputs);
            results.addAll(l);
            return results;
        }
        List<I> list = ListSupport.list(inputs);
        List<List<I>> partitions = Lists.partition(list, size);
        for (List<I> partition : partitions) {
            Collection<? extends O> l = fetcher.apply(partition);
            results.addAll(l);
        }
        return results;
    }

    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * 获取本机ip
     *
     * @return
     */
    public static String getInternetIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface f = interfaces.nextElement();
                Enumeration<InetAddress> addresses = f.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        String addr = address.getHostAddress();
                        if (!addr.startsWith("192.168.") && !addr.startsWith("127.") && !addr.startsWith("0.0.0.0")) {
                            return addr;
                        }
                    }
                }
            }
        } catch (IOException e) {
        }
        return null;
    }

    public static String getListenPort() {
        return listenPort;
    }

    public static String setListenPort(String port) {
        listenPort = port;
        return listenPort;
    }

    private static String listenPort = "7001";

    public static void main(String... args) {
        String ip = getInternetIp();
        BaseSupport.println(ip);
    }
}
