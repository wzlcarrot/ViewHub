package com.easylive.entity.constant;


//常量类,实现高内聚低耦合
public class Constants {

    public static final Integer ONE = 1;
    public static final Integer ZERO = 0;
    public static final Integer length_2 = 2;
    public static final Integer length_10 = 10;   //密码加密长度
    public static final Integer length_30 = 30;
    public static final Integer length_15 = 15;
    public static final Integer length_20 = 20;
    public static final Integer REDIS_KEY_EXPIRE_ONE_MIN = 60000;      //定义缓存过期时间
    public static final Integer REDIS_KEY_EXPIRE_ONE_SECONDS = 1000;

    public static final String REDIS_KEY_PREFIX = "easylive:";  //定义缓存前缀
    public static final String REDIS_KEY_VIDEO_SEARCH_COUNT = REDIS_KEY_PREFIX + "video:search:";

    public static String REDIS_KEY_CHECK_CODE = REDIS_KEY_PREFIX+"checkCode:"; //定义验证码缓存前缀

    public static final String REGEX_PASSWORD = "^(?=.*\\d)(?=.*[a-zA-Z])[\\da-zA-Z~!@#$%^&*_]{8,18}$"; //密码校验

    public static final Integer REDIS_KEY_EXPIRE_ONE_DAY = 86400000;

    public static final Integer TIME_SECOND_DAY = REDIS_KEY_EXPIRE_ONE_DAY/1000;

    public  static final String REDIS_KEY_TOKEN_WEB = REDIS_KEY_PREFIX+"token:web:";

    public static final String REDIS_KEY_TOKEN_ADMIN = REDIS_KEY_PREFIX+"token:admin:";

    public static final String TOKEN_WEB = "token";

    public static final  String TOKEN_ADMIN = "adminToken";

    public static String REDIS_KEY_CATEGORY_LIST = REDIS_KEY_PREFIX+"category:list:";

    public static final String FILE_FOLDER = "file/";
    
    public static final String FILE_COVER = "cover/";

    public static final String FILE_VIDEO = "video/";

    public static final String FILE_TEMP = "temp/";

    public static final String IMAGE_THUMBNAIL_SUFFIX = "_thumbnail.jpg";

    public static final String REDIS_KEY_UP_LOADING_FILE = REDIS_KEY_PREFIX +"uploading:";

    public static final String REDIS_KEY_SYS_SETTING = REDIS_KEY_PREFIX +"sysSetting:";

    public static final Long MB_SIZE = 1024*1024L;

    public static final String REDIS_KEY_FILE_DEL = REDIS_KEY_PREFIX + "file:list:del:";

    public static final String TEMP_VIDEO_NAME = "/temp.mp4";

    public static final String VIDEO_CODE_HEVC = "hevc";

    public static final String VIDEO_CODE_TEMP_FILE_SUFFIX = "_temp ";

    public static final String REDIS_KEY_QUEUE_TRANSFER = REDIS_KEY_PREFIX + "queue:transfer:";

    public static final String TS_NAME = "index.ts";

    public static final String M3U8_NAME = "index.m3u8";


    //视频在线
    public static final String REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE_PREFIX = REDIS_KEY_PREFIX + "video:play:online:";

    public static final String REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE = REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE_PREFIX + "count:%s";

    public static final String REDIS_KEY_VIDEO_PLAY_COUNT_USER_PREFIX = "user:";

    public static final String REDIS_KEY_VIDEO_PLAY_COUNT_USER = REDIS_KEY_VIDEO_PLAY_COUNT_ONLINE_PREFIX + REDIS_KEY_VIDEO_PLAY_COUNT_USER_PREFIX + "%s:%s";

    public static final String REDIS_KEY_VIDEO_PLAY_COUNT = REDIS_KEY_PREFIX + "video:playCount:";


    public static final Integer UPDATE_NICK_NAME_COIN = 5;


    public static final Integer HOUR_24 = 24;

    public static final String REDIS_KEY_QUEUE_VIDEO_PLAY = REDIS_KEY_PREFIX + "queue:video:play:";

    /**
     * 点赞 / 收藏 异步写 + 写聚合相关 Redis Key
     */
    // 视频点赞计数聚合 Hash：field=videoId, value=增量
    public static final String REDIS_KEY_VIDEO_LIKE_HASH = REDIS_KEY_PREFIX + "video:like:agg";
    // 视频收藏计数聚合 Hash：field=videoId, value=增量
    public static final String REDIS_KEY_VIDEO_COLLECT_HASH = REDIS_KEY_PREFIX + "video:collect:agg";
    // 用户行为接口幂等 Set 前缀：easylive:idem:userAction:{userId}
    public static final String REDIS_KEY_USER_ACTION_IDEMPOTENT_PREFIX = REDIS_KEY_PREFIX + "idem:userAction:";

    /**
     * 关注 / 粉丝 关系与计数
     */
    // 关系 Set：followings:{uid} 记录我关注的人，followers:{uid} 记录关注我的人
    public static final String REDIS_KEY_FOLLOWINGS_PREFIX = REDIS_KEY_PREFIX + "followings:";
    public static final String REDIS_KEY_FOLLOWERS_PREFIX = REDIS_KEY_PREFIX + "followers:";
    // 关注接口幂等前缀（可选 requestId 防重复）：easylive:idem:focus:{userId}
    public static final String REDIS_KEY_IDEM_FOCUS_PREFIX = REDIS_KEY_PREFIX + "idem:focus:";

    /**
     * RabbitMQ 相关常量
     */
    // 用户消息通知队列
    public static final String RABBITMQ_EXCHANGE_USER_MESSAGE = "user.message.exchange";
    public static final String RABBITMQ_QUEUE_USER_MESSAGE = "user.message.queue";
    public static final String RABBITMQ_ROUTING_KEY_USER_MESSAGE = "user.message.routing";
    
    // 视频播放统计队列
    public static final String RABBITMQ_EXCHANGE_VIDEO_PLAY = "video.play.exchange";
    public static final String RABBITMQ_QUEUE_VIDEO_PLAY = "video.play.queue";
    public static final String RABBITMQ_ROUTING_KEY_VIDEO_PLAY = "video.play.routing";
    
    // 视频转码队列
    public static final String RABBITMQ_EXCHANGE_VIDEO_TRANSFER = "video.transfer.exchange";
    public static final String RABBITMQ_QUEUE_VIDEO_TRANSFER = "video.transfer.queue";
    public static final String RABBITMQ_ROUTING_KEY_VIDEO_TRANSFER = "video.transfer.routing";
    
    // 视频转码死信队列
    public static final String RABBITMQ_EXCHANGE_VIDEO_TRANSFER_DLX = "video.transfer.dlx.exchange";
    public static final String RABBITMQ_QUEUE_VIDEO_TRANSFER_DLX = "video.transfer.dlx.queue";
    public static final String RABBITMQ_ROUTING_KEY_VIDEO_TRANSFER_DLX = "video.transfer.dlx.routing";
    
    // 弹幕队列
    public static final String RABBITMQ_EXCHANGE_VIDEO_DANMU = "video.danmu.exchange";
    public static final String RABBITMQ_QUEUE_VIDEO_DANMU = "video.danmu.queue";
    public static final String RABBITMQ_ROUTING_KEY_VIDEO_DANMU = "video.danmu.routing";
    
    // Redis弹幕列表Key前缀（按fileId分组）
    public static final String REDIS_KEY_DANMU_LIST = REDIS_KEY_PREFIX + "danmu:list:";
}
