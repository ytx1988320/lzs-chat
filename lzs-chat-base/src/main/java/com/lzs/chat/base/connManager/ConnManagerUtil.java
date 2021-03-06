package com.lzs.chat.base.connManager;

import com.lzs.chat.base.bean.Client;
import com.lzs.chat.base.constans.AppConstants;
import com.lzs.chat.base.protobuf.Message;
import com.lzs.chat.base.util.Maps;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * <一句话说明功能>
 * <功能详细描述>
 *
 * @author fugui
 * @title <一句话说明功能>
 * @date 2020/4/9 15:58
 * @since <版本号>
 */
@Slf4j
public class ConnManagerUtil {
    /**
     * 所有连接对象
     */
    private static ConcurrentMap<String, Client> CLIENT_MAP = Maps.newConcurrentMap();
    /**
     * 按照房间id存放 用户id
     */
    private static ConcurrentMap<Integer, List<String>> ROOM_CONN_MAP = Maps.newConcurrentMap();

    /**
     * 连接操作
     * @param connId
     * @param client
     */
    public static void clientPut(String connId, Client client) {
        CLIENT_MAP.put(connId, client);
    }

    public static Client clientGet(String connId) {
        return CLIENT_MAP.get(connId);
    }

    /**
     * 连接绑定userid
     * @param userId
     */
    public static void connBindUserId(String connId ,String userId) {
        Client client = CLIENT_MAP.get(connId);
        if(Objects.nonNull(client)){
            client.setUserId(userId);
        }
    }
    public static void clientRemove(String connId) {
        CLIENT_MAP.remove(connId);
    }

    public static int countClient() {
        return CLIENT_MAP.size();
    }

    /**
     * 房间操作
     * @param roomId
     * @param connId
     */
    public static void roomConnPut(Integer roomId, String connId) {
        List<String> connIds = ROOM_CONN_MAP.get(roomId);
        //需要枷锁
        synchronized (ROOM_CONN_MAP) {
            if (CollectionUtils.isEmpty(connIds)) {
                connIds = new ArrayList<>();
                connIds.add(connId);
                ROOM_CONN_MAP.put(roomId,connIds);
            } else {
                connIds.add(connId);
            }
        }
    }

    public static void roomConnRemove(Integer roomId, String connId){
        List<String> connIds = ROOM_CONN_MAP.get(roomId);
        long oldtime=System.currentTimeMillis();
        //需要枷锁
        synchronized (ROOM_CONN_MAP) {
            if (!CollectionUtils.isEmpty(connIds)) {
                connIds.remove(connId);
                //如果房间人数为0时，删除房间key
                if(connIds.size()<1){
                    ROOM_CONN_MAP.remove(roomId);
                }
            }
        }
        long newtime=System.currentTimeMillis();
        //当循环大于20ms打报警日志
        if(newtime-oldtime>20){
            log.warn("========删除房间人数时间有点长 roomsize:{}",connIds.size());
        }
    }

    /**
     * 总房间数
     * @return
     */
    public static int countRoom(){
       return  ROOM_CONN_MAP.size();
    }

    /**
     * 获取房间内的连接
     * @param roomId
     * @return
     */
    public static List<String> connsInRoom(Integer roomId){
        return ROOM_CONN_MAP.get(roomId);
    }
    /**
     * 总的房间id
     * @return
     */
    public static Set<Integer> roomIdList(){
        return ROOM_CONN_MAP.keySet();
    }

    /**
     * 房间总人数
     * @param roomId
     * @return
     */
    public static int countRoomConn(Integer roomId){
        List<String> connIds = ROOM_CONN_MAP.get(roomId);
        return  connIds.size();
    }

    /**
     * 给房间所有用户发信息
     * @param roomId
     * @param protocol
     */
    public static void sendMsgToRoomConn(Integer roomId, Message.Protocol protocol){
        List<String> connIds = ROOM_CONN_MAP.get(roomId);
        long oldtime=System.currentTimeMillis();
        for (String connId : connIds) {
            Client client = CLIENT_MAP.get(connId);
            if(Objects.nonNull(client)){
                Channel ch = client.getChannel();
                if(ch.isActive()){
                    ch.writeAndFlush(protocol);
                }
            }
        }
        long newtime=System.currentTimeMillis();
        //当循环大于50ms打报警日志
        if(newtime-oldtime>50){
            log.warn("========房间人数发消息时间有点长 roomsize:{}",connIds.size());
        }
    }

    /**
     * 给房间其他用户发信息
     * @param roomId
     * @param connId
     * @param protocol
     */
    public static void sendMsgToRoomOtherConn(Integer roomId,String connId, Message.Protocol protocol){
        List<String> connIds = ROOM_CONN_MAP.get(roomId);
        long oldtime=System.currentTimeMillis();
        for (String conn : connIds) {
            if(!connId.equals(conn)){
                Client client = CLIENT_MAP.get(conn);
                if(Objects.nonNull(client)){
                    Channel ch = client.getChannel();
                    if(ch.isActive()){
                        ch.writeAndFlush(protocol);
                    }
                }
            }
        }
        long newtime=System.currentTimeMillis();
        //当循环大于50ms打报警日志
        if(newtime-oldtime>50){
            log.warn("========房间人数发消息时间有点长 roomsize:{}",connIds.size());
        }
    }

    /**
     * 关闭连接
     * @param ch
     */
    public static void closeConn(Channel ch){
        String connId = ch.attr(AppConstants.KEY_CONN_ID).get();
        Integer roomId = ch.attr(AppConstants.KEY_ROOM_ID).get();
        if (StringUtils.isNotBlank(connId)) {
            clientRemove(connId);
        }
        if(Objects.nonNull(roomId)){
            roomConnRemove(roomId,connId);
        }
        if(ch.isActive()){
            ch.close();
        }
    }
}
